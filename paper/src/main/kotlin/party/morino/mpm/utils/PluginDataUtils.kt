/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import party.morino.mpm.api.model.plugin.PluginData
import java.io.File
import java.util.jar.JarFile

object PluginDataUtils {
    /**
     * plugin.yml / paper-plugin.yml の生テキストから api-version を書かれたままの表記で取り出す
     *
     * SnakeYAML はクォートなしの `api-version: 1.20` を Double 1.2 に、`1.9` を Double 1.9 に変換してしまい、
     * パース後の値からは「1.2 / 1.20」「1.9 / 1.90」を区別できない。
     * そのため YAML ローダーを通す前のテキストを行単位で走査し、作者が書いた文字列をそのまま返す。
     *
     * - 行頭（トップレベル）の `api-version:` のみを対象にし、ネストしたキーは拾わない
     * - シングル/ダブルクォートは剥がす
     * - 行末の `# コメント` は取り除く
     * - CRLF 改行にも対応する
     *
     * @param yamlText plugin.yml / paper-plugin.yml の内容
     * @return api-version の文字列。見つからない、または空の場合は null
     */
    internal fun extractRawApiVersion(yamlText: String): String? {
        // 先頭に UTF-8 BOM があると1行目の `^` にマッチしないため取り除く
        val match = RAW_API_VERSION_REGEX.find(yamlText.removePrefix("\uFEFF")) ?: return null
        val value =
            match.groupValues[1]
                .trim()
                // クォートされていない値のみ、行末コメントを取り除く（`1.20 # comment` のような書き方）
                .let { raw ->
                    if (raw.startsWith("'") || raw.startsWith("\"")) raw else raw.substringBefore(" #").trim()
                }
                // クォートを剥がす（`'1.20'` / `"1.20"`）
                .removeSurrounding("'")
                .removeSurrounding("\"")
                .trim()
        return value.ifBlank { null }
    }

    /**
     * SnakeYAML がパースした api-version の値を文字列に変換する（フォールバック用）
     *
     * 通常は [extractRawApiVersion] で生テキストから取得するため、ここに来るのは
     * 生テキストから取り出せなかった場合だけである。
     * Number 型に変換されている場合は元の表記を復元できないため、そのまま文字列化する。
     * （以前は「小数部が1桁なら 0 を補う」ヒューリスティックで 1.9 を 1.90 にしてしまい、
     * 1.21 サーバーで誤って非互換と判定していた）
     */
    internal fun parseApiVersion(raw: Any?): String {
        if (raw == null) return ""
        // String型ならそのまま返す（クォートされたYAML値）
        if (raw is String) return raw
        return raw.toString()
    }

    /**
     * 行頭の `api-version:` を捕捉する正規表現
     *
     * `[ \t]*` で区切りを許容し、値は行末（CR を除く）まで取り込む
     */
    private val RAW_API_VERSION_REGEX = Regex("""^api-version:[ \t]*([^\r\n]*)""", RegexOption.MULTILINE)

    fun getPluginData(file: File): PluginData? {
        // JarFileをuse{}で確実にクローズする（リソースリーク防止）
        return JarFile(file).use { jarFile ->
            // paper-plugin.ymlを先にチェック（Paperプラグインの場合、両方存在する可能性があるため）
            val paperYml = jarFile.getEntry("paper-plugin.yml")
            if (paperYml != null) {
                return@use getPaperPluginData(jarFile)
            }
            val pluginYml = jarFile.getEntry("plugin.yml")
            if (pluginYml != null) {
                return@use getBukkitPluginData(jarFile)
            }
            null
        }
    }

    private fun getPaperPluginData(jarFile: JarFile): PluginData.PaperPluginData {
        val paperYml = jarFile.getEntry("paper-plugin.yml")
        // InputStream/BufferedReaderをuse{}で確実にクローズする（リソースリーク防止）
        // api-version を書かれたままの表記で取り出すため、テキストを一度読み込んでから YAML としてパースする
        val yamlText = jarFile.getInputStream(paperYml).bufferedReader().use { it.readText() }
        val yamlData = Yaml(SafeConstructor(LoaderOptions())).load<Map<String, Any>>(yamlText)
        val name = (yamlData["name"] ?: "").toString()
        val version = (yamlData["version"] ?: "").toString()
        val main = (yamlData["main"] ?: "").toString()
        val description = (yamlData["description"] ?: "").toString()
        val apiVersion = extractRawApiVersion(yamlText) ?: parseApiVersion(yamlData["api-version"])
        val bootstrapper = (yamlData["bootstrapper"] ?: "").toString()
        val loader = (yamlData["loader"] ?: "").toString()
        val author = (yamlData["author"] ?: "").toString()
        val website = (yamlData["website"] ?: "").toString()

        // Paper形式の依存関係を解析（dependencies セクション内にserver/bootstrapがある）
        val dependencies = yamlData["dependencies"] as? Map<*, *>

        // serverセクションとbootstrapセクションから依存関係を収集
        val serverDeps = dependencies?.get("server") as? Map<*, *>
        val bootstrapDeps = dependencies?.get("bootstrap") as? Map<*, *>

        // 必須依存を収集
        val depend = mutableListOf<String>()
        serverDeps
            ?.filterValues { it is Map<*, *> && (it as Map<*, *>)["required"] == true }
            ?.keys
            ?.forEach { depend.add(it.toString()) }
        bootstrapDeps
            ?.filterValues { it is Map<*, *> && (it as Map<*, *>)["required"] == true }
            ?.keys
            ?.forEach { if (!depend.contains(it.toString())) depend.add(it.toString()) }

        // オプション依存を収集
        val softDepend = mutableListOf<String>()
        serverDeps
            ?.filterValues { it is Map<*, *> && (it as Map<*, *>)["required"] != true }
            ?.keys
            ?.forEach { softDepend.add(it.toString()) }
        bootstrapDeps
            ?.filterValues { it is Map<*, *> && (it as Map<*, *>)["required"] != true }
            ?.keys
            ?.forEach { if (!softDepend.contains(it.toString())) softDepend.add(it.toString()) }

        // loadBeforeを収集（Paper形式ではload: AFTERで表現される）
        val loadBefore = mutableListOf<String>()
        serverDeps
            ?.filterValues { it is Map<*, *> && (it as Map<*, *>)["load"] == "BEFORE" }
            ?.keys
            ?.forEach { loadBefore.add(it.toString()) }

        return PluginData.PaperPluginData(
            name,
            version,
            main,
            description,
            apiVersion,
            bootstrapper,
            loader,
            author,
            website,
            depend,
            softDepend,
            loadBefore
        )
    }

    private fun getBukkitPluginData(jarFile: JarFile): PluginData.BukkitPluginData {
        val pluginYml = jarFile.getEntry("plugin.yml")
        // InputStream/BufferedReaderをuse{}で確実にクローズする（リソースリーク防止）
        // api-version を書かれたままの表記で取り出すため、テキストを一度読み込んでから YAML としてパースする
        val yamlText = jarFile.getInputStream(pluginYml).bufferedReader().use { it.readText() }
        val yamlData = Yaml(SafeConstructor(LoaderOptions())).load<Map<String, Any>>(yamlText)
        val name = (yamlData["name"] ?: "").toString()
        val version = (yamlData["version"] ?: "").toString()
        val main = (yamlData["main"] ?: "").toString()
        val description = (yamlData["description"] ?: "").toString()
        val author = (yamlData["author"] ?: "").toString()
        val website = (yamlData["website"] ?: "").toString()
        val apiVersion = extractRawApiVersion(yamlText) ?: parseApiVersion(yamlData["api-version"])

        // Bukkit形式の依存関係を解析
        val depend = parseStringList(yamlData["depend"])
        val softDepend = parseStringList(yamlData["softdepend"])
        val loadBefore = parseStringList(yamlData["loadbefore"])

        return PluginData.BukkitPluginData(
            name,
            version,
            main,
            description,
            author,
            website,
            apiVersion,
            depend,
            softDepend,
            loadBefore
        )
    }

    /**
     * YAMLの値をString型のリストに変換する
     * @param value YAMLから読み込んだ値
     * @return 文字列のリスト
     */
    private fun parseStringList(value: Any?): List<String> =
        when (value) {
            is List<*> -> value.filterIsInstance<String>()
            is String -> listOf(value)
            else -> emptyList()
        }
}