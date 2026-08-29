/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.cache

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import party.morino.mpm.api.domain.config.ConfigManager
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.config.model.CacheSettings
import party.morino.mpm.api.domain.config.model.ConfigData
import party.morino.mpm.api.domain.config.model.GlobalSettings
import party.morino.mpm.mock.config.TempPluginDirectory
import java.io.File

/**
 * HttpMetadataCacheImplのTTL判定と保存・読み出しを検証するテスト
 */
@DisplayName("HttpMetadataCache - TTL based metadata cache")
class HttpMetadataCacheImplTest {
    @TempDir
    lateinit var tempDir: File

    // テストごとに変更できるTTL（秒）
    private var ttlSeconds: Long = 300

    // テストごとに変更できるエントリ数の上限
    private var maxEntries: Int = 200

    private val json = Json { ignoreUnknownKeys = true }

    private val requestUrl = "https://hangar.papermc.io/api/v1/projects/kennytv/Maintenance/versions"

    @BeforeEach
    fun setUp() {
        // 前のテストのコンテキストが残っている場合に備えて停止しておく
        GlobalContext.stopKoin()
        // 一時ディレクトリを基点にしたKoinコンテキストを起動する
        val testModule =
            module {
                single<PluginDirectory> { TempPluginDirectory(tempDir) }
                single<ConfigManager> {
                    object : ConfigManager {
                        override fun getConfig(): ConfigData =
                            ConfigData(
                                settings =
                                    GlobalSettings(
                                        cache =
                                            CacheSettings(
                                                enabled = true,
                                                metadataTtlSeconds = ttlSeconds,
                                                maxMetadataEntries = maxEntries
                                            )
                                    )
                            )

                        override suspend fun reload() { /* テストでは不要 */ }
                    }
                }
            }
        GlobalContext.startKoin { modules(testModule) }
    }

    @AfterEach
    fun tearDown() {
        GlobalContext.stopKoin()
    }

    @Test
    @DisplayName("put then get returns the cached body")
    fun putThenGetReturnsBody() {
        val cache = HttpMetadataCacheImpl()
        cache.put(requestUrl, "{\"result\":\"ok\"}")

        assertEquals("{\"result\":\"ok\"}", cache.get(requestUrl).getOrNull())
    }

    @Test
    @DisplayName("get returns none when the entry is older than the TTL")
    fun getReturnsNoneWhenExpired() {
        val cache = HttpMetadataCacheImpl()
        cache.put(requestUrl, "{\"result\":\"ok\"}")

        // 取得時刻をTTLより過去へ書き換えて、期限切れの状態を再現する
        val entryFile = metadataFiles().single()
        val entry = json.decodeFromString(CachedMetadataEntry.serializer(), entryFile.readText())
        val expiredEntry = entry.copy(fetchedAtEpochSeconds = entry.fetchedAtEpochSeconds - ttlSeconds - 1)
        entryFile.writeText(json.encodeToString(CachedMetadataEntry.serializer(), expiredEntry))

        assertTrue(cache.get(requestUrl).isNone())
    }

    @Test
    @DisplayName("put is skipped while caching is disabled by the TTL")
    fun putIsSkippedWhenDisabled() {
        // TTLが0以下の場合はキャッシュを使用しない
        ttlSeconds = 0
        val cache = HttpMetadataCacheImpl()
        cache.put(requestUrl, "{\"result\":\"ok\"}")

        assertTrue(metadataFiles().isEmpty())
        assertTrue(cache.get(requestUrl).isNone())
    }

    @Test
    @DisplayName("put replaces an existing entry in place")
    fun putReplacesExistingEntry() {
        val cache = HttpMetadataCacheImpl()
        cache.put(requestUrl, "{\"result\":\"first\"}")
        cache.put(requestUrl, "{\"result\":\"second\"}")

        // 一時ファイルが残らず、エントリが上書きされていること
        assertEquals(1, metadataFiles().size)
        assertEquals("{\"result\":\"second\"}", cache.get(requestUrl).getOrNull())
    }

    @Test
    @DisplayName("put evicts entries beyond the configured maximum")
    fun putEvictsEntriesOverTheLimit() {
        maxEntries = 5
        val cache = HttpMetadataCacheImpl()

        // 検索のようにURLが毎回変わるリクエストを繰り返し、退避が動作することを確認する
        repeat(HttpMetadataCacheImpl.CLEANUP_INTERVAL.toInt()) { index ->
            cache.put("https://example.com/search?q=$index", "{\"index\":$index}")
        }

        assertEquals(maxEntries, metadataFiles().size)
    }

    /**
     * メタデータキャッシュのエントリファイル一覧を返す
     */
    private fun metadataFiles(): List<File> {
        val metadataDir = File(tempDir, "cache/${CacheDirectories.METADATA}")
        return metadataDir.listFiles()?.toList() ?: emptyList()
    }
}