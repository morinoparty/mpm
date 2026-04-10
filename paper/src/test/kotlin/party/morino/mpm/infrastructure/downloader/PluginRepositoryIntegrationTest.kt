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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import party.morino.mpm.api.domain.downloader.model.UrlData
import party.morino.mpm.api.domain.downloader.model.VersionData
import party.morino.mpm.api.domain.plugin.model.VersionDetail
import party.morino.mpm.infrastructure.downloader.github.GithubDownloader
import party.morino.mpm.infrastructure.downloader.modrinth.ModrinthDownloader
import party.morino.mpm.infrastructure.downloader.spigot.SpigotDownloader
import party.morino.mpm.utils.TestConfigLoader
import java.io.File
import java.util.stream.Stream

/**
 * repo/public/paper/plugins/ 配下の全プラグインJSONに対して、
 * 各リポジトリの `getAllVersions` が正常に動作することを検証するインテグレーションテスト。
 *
 * 実際のAPIにリクエストを送信するため、ネットワーク接続が必要。
 * レート制限や上流の一時的障害で落ちる可能性があるため、`integration` タグを付与して
 * デフォルトの `./gradlew test` からは除外し、`./gradlew integrationTest` でのみ実行する。
 *
 * 検証内容:
 * 1. getAllVersions がエラーにならず、1件以上のバージョンを返す
 * 2. 各バージョンエントリが空でない version/downloadId を持つ
 * 3. リポジトリ設定でチャンネル（latest/beta/alpha）が定義されている場合、
 *    各チャンネルに属するバージョンを [versionMatcher] でフィルタし、
 *    チャンネルごとの [versionModifier] で正規化した結果に重複がないこと
 *    （チャンネル未定義の場合はリポジトリルートの [versionModifier] で一括チェック）
 */
@Tag("integration")
class PluginRepositoryIntegrationTest {

    companion object {
        // JSONパーサー（未知フィールドを無視して $schema などを許容する）
        private val json = Json { ignoreUnknownKeys = true }

        // テスト用設定をresources/plugins/mpm/config.jsonから読み込む
        // config.local.json が存在すればそちらを優先（GitHub tokenなどのローカル上書き用）
        private val testConfig = TestConfigLoader.load()

        // 各プラットフォームのダウンローダーインスタンス
        // GitHub は rate limit 緩和のため config の githubToken を使う
        private val modrinthDownloader = ModrinthDownloader()
        private val spigotDownloader = SpigotDownloader()
        private val githubDownloader = GithubDownloader(testConfig.settings.githubToken)

        /**
         * 1チャンネル分の設定。matcher/modifier/useUpstreamLabel いずれも省略可能
         */
        data class TestChannel(
            val name: String,
            val versionMatcher: String?,
            val versionModifier: String?,
            val useUpstreamLabel: Boolean = false,
        )

        /**
         * 1リポジトリ分のテスト設定
         *
         * @property rootVersionModifier リポジトリルートの versionModifier（フォールバック用）
         * @property channels 定義されているチャンネル設定（空の場合は「単一チャンネル」として扱う）
         */
        data class TestRepository(
            val pluginId: String,
            val type: String,
            val id: String,
            val rootVersionModifier: String?,
            val channels: List<TestChannel>,
        ) {
            // パラメータ化テストの表示名に使うため、簡潔な1行表現にする
            override fun toString(): String = "$pluginId ($type: $id)"
        }

        /**
         * プラグインJSONディレクトリのパスを解決する
         */
        private fun resolvePluginDir(): File {
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
         * JSON内のチャンネルオブジェクトから [TestChannel] を構築する
         */
        private fun parseChannel(name: String, obj: JsonObject?): TestChannel? {
            if (obj == null) return null
            val matcher = obj["versionMatcher"]?.jsonPrimitive?.content
            val modifier = obj["versionModifier"]?.jsonPrimitive?.content
            val useUpstreamLabel = obj["useUpstreamLabel"]?.jsonPrimitive?.content?.toBoolean() ?: false
            // すべて未指定なら空のチャンネル定義として扱う意味がないのでnullで返す
            if (matcher == null && modifier == null && !useUpstreamLabel) return null
            return TestChannel(
                name = name,
                versionMatcher = matcher,
                versionModifier = modifier,
                useUpstreamLabel = useUpstreamLabel,
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
                // repositories は必須。欠落はカタログの不正として即座に検出する
                val repositories = jsonObj["repositories"]?.jsonArray
                    ?: throw IllegalStateException(
                        "${file.name}: 'repositories' フィールドが存在しません。" +
                            "プラグインJSONには必ず repositories 配列を含めてください"
                    )
                check(repositories.isNotEmpty()) {
                    "${file.name}: 'repositories' 配列が空です。" +
                        "少なくとも1つのリポジトリ定義が必要です"
                }

                repositories.map { repoElement ->
                    val repoObj = repoElement.jsonObject
                    val type = repoObj["type"]?.jsonPrimitive?.content ?: "unknown"
                    val id = repoObj["id"]?.jsonPrimitive?.content ?: "unknown"
                    val rootModifier = repoObj["versionModifier"]?.jsonPrimitive?.content
                    val channels = listOfNotNull(
                        parseChannel("latest", repoObj["latest"]?.jsonObject),
                        parseChannel("beta", repoObj["beta"]?.jsonObject),
                        parseChannel("alpha", repoObj["alpha"]?.jsonObject),
                    )
                    Arguments.of(
                        TestRepository(
                            pluginId = pluginId,
                            type = type,
                            id = id,
                            rootVersionModifier = rootModifier,
                            channels = channels,
                        )
                    )
                }
            }.stream()
        }
    }

    /**
     * 指定リポジトリから全バージョンを取得する（プラットフォーム振り分け）
     */
    private suspend fun fetchAllVersions(type: String, repoId: String): List<VersionData> =
        when (type) {
            "modrinth" -> modrinthDownloader.getAllVersions(UrlData.ModrinthUrlData(repoId))
            "spigotmc" -> spigotDownloader.getAllVersions(UrlData.SpigotMcUrlData(repoId))
            "github" -> {
                val parts = repoId.split("/")
                githubDownloader.getAllVersions(UrlData.GithubUrlData(parts[0], parts[1]))
            }
            else -> throw IllegalArgumentException("未対応のリポジトリタイプ: $type")
        }

    /**
     * `useUpstreamLabel` チャンネルをプラットフォームのネイティブラベル経由で解決する
     *
     * Modrinthは `version_type`、GitHubは `prerelease` フラグを使う。
     * `latest`/`release` は `getLatestVersion`、それ以外は `getLatestVersionByTag` に委譲する。
     */
    private suspend fun resolveViaUpstreamLabel(
        type: String,
        repoId: String,
        channel: String,
    ): VersionData? = try {
        val lower = channel.lowercase()
        when (type) {
            "modrinth" -> {
                val urlData = UrlData.ModrinthUrlData(repoId)
                if (lower == "latest" || lower == "release") {
                    modrinthDownloader.getLatestVersion(urlData)
                } else {
                    modrinthDownloader.getLatestVersionByTag(urlData, channel)
                }
            }
            "github" -> {
                val parts = repoId.split("/")
                val urlData = UrlData.GithubUrlData(parts[0], parts[1])
                if (lower == "latest" || lower == "release") {
                    githubDownloader.getLatestVersion(urlData)
                } else {
                    githubDownloader.getLatestVersionByTag(urlData, channel)
                }
            }
            // Spigotmcは useUpstreamLabel 非対応
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    /**
     * 1チャンネル分のバージョン集合に対して、重複正規化チェックと基本検証を行う
     *
     * @return 失敗メッセージのリスト（空なら合格）
     */
    private fun validateChannelGroup(
        label: String,
        versions: List<VersionData>,
        modifier: String?,
    ): List<String> {
        if (versions.isEmpty()) return emptyList() // 空チャンネルは検証対象外（スキップ）

        val errors = mutableListOf<String>()
        val normalized = versions.map { v ->
            v.version to VersionDetail.normalizeWithPattern(v.version, modifier)
        }

        // 各バージョンエントリの基本検証
        versions.forEach { v ->
            if (v.version.isBlank()) errors += "$label: version が空のエントリあり"
            if (v.downloadId.isBlank()) errors += "$label: downloadId が空のエントリあり"
        }

        // 正規化後の重複を検出
        // ただし、上流APIが同一rawバージョン文字列を複数回返すケース（実データの重複）は
        // 我々の管轄外なので無視し、「異なるrawが同じ正規化結果に潰れる」ケースのみを
        // versionModifierの不足として検出する
        val duplicates = normalized
            .groupBy { it.second }
            .filter { (_, pairs) -> pairs.map { it.first }.distinct().size > 1 }
        if (duplicates.isNotEmpty()) {
            val detail = duplicates.entries.joinToString("; ") { (norm, pairs) ->
                val distinctRaws = pairs.map { it.first }.distinct()
                "'$norm' <- [${distinctRaws.joinToString(", ")}]"
            }
            errors += "$label: versionModifierでは区別できない複数バージョンあり: $detail"
        }

        // 各バージョンのraw -> modified全件を表示（カタログ調整の判断材料とするため）
        println("  [$label] ${versions.size} versions (modifier=${modifier ?: "default"}):")
        normalized.forEach { (raw, norm) ->
            val marker = if (raw == norm) "  " else " *" // 変換が発生したものを * で強調
            println("    $marker $raw -> $norm")
        }
        return errors
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pluginRepositoryProvider")
    @DisplayName("getAllVersions per-channel validation")
    fun getAllVersionsPerChannelValidation(repo: TestRepository) {
        runBlocking {
            val allVersions = fetchAllVersions(repo.type, repo.id)
            println("=== ${repo.pluginId} (${repo.type}: ${repo.id}) — ${allVersions.size} total versions ===")

            assertTrue(
                allVersions.isNotEmpty(),
                "${repo.pluginId} (${repo.type}: ${repo.id}) のバージョンリストが空です"
            )

            val errors = mutableListOf<String>()

            if (repo.channels.isEmpty()) {
                // チャンネル未定義: 全バージョンを1グループとしてルートmodifierで正規化チェック
                errors += validateChannelGroup(
                    label = "all",
                    versions = allVersions,
                    modifier = repo.rootVersionModifier,
                )
            } else {
                // チャンネル定義あり: 各チャンネルを個別に検証
                for (channel in repo.channels) {
                    val matcherRegex = channel.versionMatcher?.let { runCatching { Regex(it) }.getOrNull() }
                    val effectiveModifier = channel.versionModifier ?: repo.rootVersionModifier
                    when {
                        // (1) versionMatcherがある: regexでフィルタしてから検証
                        matcherRegex != null -> {
                            val filtered = allVersions.filter { matcherRegex.containsMatchIn(it.version) }
                            errors += validateChannelGroup(
                                label = channel.name,
                                versions = filtered,
                                modifier = effectiveModifier,
                            )
                        }
                        // (2) useUpstreamLabelのみ: プラットフォームネイティブ解決で動作確認
                        //     （version_typeやprereleaseフラグはここから見えないので、
                        //      ダウンローダー経由で最新1件を取得できることだけを検証する）
                        channel.useUpstreamLabel -> {
                            val resolved = resolveViaUpstreamLabel(repo.type, repo.id, channel.name)
                            if (resolved == null) {
                                errors += "${channel.name}: useUpstreamLabel による解決に失敗しました"
                            } else {
                                val norm = VersionDetail.normalizeWithPattern(resolved.version, effectiveModifier)
                                println("  [${channel.name}] upstream-label resolved -> ${resolved.version} -> $norm")
                            }
                        }
                        // (3) modifierのみ指定: 全バージョンをそのmodifierで正規化チェック
                        else -> {
                            errors += validateChannelGroup(
                                label = channel.name,
                                versions = allVersions,
                                modifier = effectiveModifier,
                            )
                        }
                    }
                }
            }

            assertTrue(
                errors.isEmpty(),
                "${repo.pluginId} (${repo.type}: ${repo.id}) にカタログ不整合があります:\n" +
                    errors.joinToString("\n") { "  - $it" }
            )

            println("✓ ${repo.pluginId} (${repo.type}) passed per-channel validation")
        }
    }
}
