/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils

import org.yaml.snakeyaml.Yaml
import party.morino.mpm.api.model.plugin.PluginData
import java.io.File
import java.util.jar.JarFile

object PluginDataUtils {
    fun getPluginData(file: File): PluginData? {
        val jarFile = JarFile(file)
        // paper-plugin.ymlを先にチェック（Paperプラグインの場合、両方存在する可能性があるため）
        val paperYml = jarFile.getEntry("paper-plugin.yml")
        if (paperYml != null) {
            return getPaperPluginData(jarFile)
        }
        val pluginYml = jarFile.getEntry("plugin.yml")
        if (pluginYml != null) {
            return getBukkitPluginData(jarFile)
        }
        return null
    }

    private fun getPaperPluginData(jarFile: JarFile): PluginData.PaperPluginData {
        val paperYml = jarFile.getEntry("paper-plugin.yml")
        val paperYmlStream = jarFile.getInputStream(paperYml)
        val paperYmlReader = paperYmlStream.bufferedReader()
        val yaml = Yaml()
        val yamlData = yaml.load<Map<String, Any>>(paperYmlReader)
        val name = (yamlData["name"] ?: "").toString()
        val version = (yamlData["version"] ?: "").toString()
        val main = (yamlData["main"] ?: "").toString()
        val description = (yamlData["description"] ?: "").toString()
        val apiVersion = (yamlData["api-version"] ?: "").toString()
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
        val pluginYmlStream = jarFile.getInputStream(pluginYml)
        val pluginYmlReader = pluginYmlStream.bufferedReader()
        val yaml = Yaml()
        val yamlData = yaml.load<Map<String, Any>>(pluginYmlReader)
        val name = (yamlData["name"] ?: "").toString()
        val version = (yamlData["version"] ?: "").toString()
        val main = (yamlData["main"] ?: "").toString()
        val description = (yamlData["description"] ?: "").toString()
        val author = (yamlData["author"] ?: "").toString()
        val website = (yamlData["website"] ?: "").toString()
        val apiVersion = (yamlData["api-version"] ?: "").toString()

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