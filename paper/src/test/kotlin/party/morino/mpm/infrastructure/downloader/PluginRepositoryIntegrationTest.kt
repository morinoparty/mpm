/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.downloader

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import party.morino.mpm.api.domain.downloader.model.UrlData
import party.morino.mpm.api.domain.plugin.model.VersionDetail
import party.morino.mpm.infrastructure.downloader.github.GithubDownloader
import party.morino.mpm.infrastructure.downloader.modrinth.ModrinthDownloader
import party.morino.mpm.infrastructure.downloader.spigot.SpigotDownloader
import java.io.File
import java.util.stream.Stream

/**
 * repo/public/paper/plugins/ 配下の全プラグインJSONに対して、
 * 各リポジトリのgetAllVersionsが正常に動作することを検証するインテグレーションテスト。
 *
 * 実際のAPIにリクエストを送信するため、ネットワーク接続が必要。
 */
class PluginRepositoryIntegrationTest {

    companion object {
        // JSONパーサー
        private val json = Json { ignoreUnknownKeys = true }

        // 各プラットフォームのダウンローダーインスタンス
        private val modrinthDownloader = ModrinthDownloader()
        private val spigotDownloader = SpigotDownloader()
        private val githubDownloader = GithubDownloader()

        /**
         * プラグインJSONディレクトリのパスを解決する
         * テスト実行ディレクトリに応じて正しいパスを返す
         */
        private fun resolvePluginDir(): File {
            // Gradleからの実行: カレントディレクトリがプロジェクトルート or paperサブプロジェクト
            val candidates = listOf(
                File("repo/public/paper/plugins"),
                File("../repo/public/paper/plugins")
            )
            return candidates.firstOrNull { it.exists() && it.isDirectory }
                ?: throw IllegalStateException(
                    "プラグインJSONディレクトリが見つかりません: ${candidates.map { it.absolutePath }}"
                )
        }

        /**
         * 全プラグインJSONからテストパラメータを生成する
         * 各リポジトリエントリを個別のテストケースとして返す
         */
        @JvmStatic
        fun pluginRepositoryProvider(): Stream<Arguments> {
            val pluginDir = resolvePluginDir()
            val jsonFiles = pluginDir.listFiles { file -> file.extension == "json" }
                ?: return Stream.empty()

            return jsonFiles.sorted().flatMap { file ->
                val content = file.readText()
                val jsonObj = json.parseToJsonElement(content).jsonObject
                val pluginId = jsonObj["id"]?.jsonPrimitive?.content ?: file.nameWithoutExtension
                val repositories = jsonObj["repositories"]?.jsonArray ?: return@flatMap emptyList()

                repositories.map { repoElement ->
                    val repoObj = repoElement.jsonObject
                    val type = repoObj["type"]?.jsonPrimitive?.content ?: "unknown"
                    val id = repoObj["id"]?.jsonPrimitive?.content ?: "unknown"
                    // versionModifierはバージョン文字列から正規化されたセマンティックバージョンを
                    // 抽出するための正規表現パターン（オプション）
                    val versionModifier = repoObj["versionModifier"]?.jsonPrimitive?.content
                    Arguments.of(pluginId, type, id, versionModifier)
                }
            }.stream()
        }
    }

    @ParameterizedTest(name = "{0} ({1}: {2})")
    @MethodSource("pluginRepositoryProvider")
    @DisplayName("getAllVersions returns non-empty list")
    fun getAllVersionsReturnsNonEmptyList(
        pluginId: String,
        repoType: String,
        repoId: String,
        versionModifier: String?
    ) {
        runBlocking {
            val versions = when (repoType) {
                "modrinth" -> {
                    val urlData = UrlData.ModrinthUrlData(repoId)
                    modrinthDownloader.getAllVersions(urlData)
                }
                "spigotmc" -> {
                    val urlData = UrlData.SpigotMcUrlData(repoId)
                    spigotDownloader.getAllVersions(urlData)
                }
                "github" -> {
                    // GitHub IDは "owner/repo" 形式
                    val parts = repoId.split("/")
                    val urlData = UrlData.GithubUrlData(parts[0], parts[1])
                    githubDownloader.getAllVersions(urlData)
                }
                else -> {
                    throw IllegalArgumentException("未対応のリポジトリタイプ: $repoType")
                }
            }

            // versionModifier（= versionPattern）で正規化したバージョン文字列に変換
            // modifier未指定の場合はデフォルトsemverパターンで正規化される
            val normalizedVersions = versions.map { versionData ->
                versionData.version to VersionDetail.normalizeWithPattern(versionData.version, versionModifier)
            }
            println(normalizedVersions.joinToString("\n") { "${it.first} -> ${it.second}" })

            assertTrue(
                versions.isNotEmpty(),
                "$pluginId ($repoType: $repoId) のバージョンリストが空です"
            )

            // 各バージョンが有効な値を持つことを確認
            versions.forEach { version ->
                assertTrue(
                    version.version.isNotBlank(),
                    "$pluginId ($repoType: $repoId) にバージョン名が空のエントリがあります"
                )
                assertTrue(
                    version.downloadId.isNotBlank(),
                    "$pluginId ($repoType: $repoId) にdownloadIdが空のエントリがあります"
                )
            }

            // 正規化後のバージョンに重複がないことを確認
            // 同じ正規化結果になるバージョンが存在するとバージョン比較が破綻するため、
            // versionModifierが正しく機能していることを保証する
            val duplicates = normalizedVersions
                .groupBy { it.second }
                .filter { it.value.size > 1 }
            assertTrue(
                duplicates.isEmpty(),
                "$pluginId ($repoType: $repoId) に正規化後の重複バージョンがあります: " +
                    duplicates.entries.joinToString("; ") { (normalized, pairs) ->
                        "'$normalized' <- [${pairs.joinToString(", ") { it.first }}]"
                    }
            )

            println(
                "✓ $pluginId ($repoType): ${versions.size} versions found, " +
                    "latest: ${normalizedVersions.first().second}"
            )
        }
    }
}
