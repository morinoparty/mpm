/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.cache

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
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
import java.nio.file.Files

/**
 * CacheManagerImplのサイズ集計と削除を検証するテスト
 */
@DisplayName("CacheManager - size and clean")
class CacheManagerImplTest {
    @TempDir
    lateinit var tempDir: File

    @BeforeEach
    fun setUp() {
        // 前のテストのコンテキストが残っている場合に備えて停止しておく
        GlobalContext.stopKoin()
        val testModule =
            module {
                single<PluginDirectory> { TempPluginDirectory(tempDir) }
                single<ConfigManager> {
                    object : ConfigManager {
                        override fun getConfig(): ConfigData =
                            ConfigData(
                                settings =
                                    GlobalSettings(
                                        cache = CacheSettings(enabled = true, metadataTtlSeconds = 300)
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
    @DisplayName("size counts every cached entry")
    fun sizeCountsEntries() {
        val cache = HttpMetadataCacheImpl()
        cache.put("https://example.com/a", "{\"a\":1}")
        cache.put("https://example.com/b", "{\"b\":2}")

        val info = CacheManagerImpl().size().getOrNull()

        assertEquals(2, info?.entryCount)
        assertTrue((info?.totalSizeBytes ?: 0) > 0)
        // 保存直後のエントリはTTL内なので期限切れ扱いにならない
        assertEquals(0, info?.expiredEntryCount)
    }

    @Test
    @DisplayName("clean removes all entries and reports freed bytes")
    fun cleanRemovesEntries() {
        val cache = HttpMetadataCacheImpl()
        cache.put("https://example.com/a", "{\"a\":1}")
        cache.put("https://example.com/b", "{\"b\":2}")

        val manager = CacheManagerImpl()
        val result = manager.clean().getOrNull()

        assertEquals(2, result?.removedEntries)
        assertTrue((result?.freedBytes ?: 0) > 0)
        // 削除後は一覧が空になる
        assertTrue(manager.list().getOrNull()?.isEmpty() == true)
    }

    @Test
    @DisplayName("clean does not follow symlinks out of the cache")
    fun cleanDoesNotFollowSymlinks() {
        // キャッシュディレクトリの外（cacheの兄弟）に通常ファイルを用意する
        val outsideDir = File(tempDir, "outside-${System.nanoTime()}")
        outsideDir.mkdirs()
        val outsideFile = File(outsideDir, "important.dat")
        outsideFile.writeText("must survive")

        // cache/link -> outsideDir というシンボリックリンクを張る
        val cacheDir = TempPluginDirectory(tempDir).getCacheDirectory()
        assumeTrue(createSymlink(File(cacheDir, "link"), outsideDir))

        val result = CacheManagerImpl().clean().getOrNull()

        // リンク先の外部ファイルは削除されず、削除件数にも含まれない
        assertTrue(outsideFile.exists())
        assertEquals(0, result?.removedEntries)
    }

    @Test
    @DisplayName("expired clean ignores a symlinked metadata directory")
    fun expiredCleanIgnoresSymlinkedMetadataDir() {
        // キャッシュディレクトリの外（cacheの兄弟）にJSONを用意する
        val outsideDir = File(tempDir, "outside-metadata-${System.nanoTime()}")
        outsideDir.mkdirs()
        val outsideEntry = File(outsideDir, "entry.json")
        outsideEntry.writeText("{\"broken\":true}")

        // cache/metadata 自体をシンボリックリンクに置き換える
        val cacheDir = TempPluginDirectory(tempDir).getCacheDirectory()
        assumeTrue(createSymlink(File(cacheDir, CacheDirectories.METADATA), outsideDir))

        val result = CacheManagerImpl().clean(expiredOnly = true).getOrNull()

        // デコードできないJSONでも外部ファイルは削除されない
        assertTrue(outsideEntry.exists())
        assertEquals(0, result?.removedEntries)
    }

    /**
     * シンボリックリンクを作成する（作成できない環境ではfalseを返す）
     */
    private fun createSymlink(
        link: File,
        target: File
    ): Boolean =
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath())
            true
        } catch (e: Exception) {
            // シンボリックリンクを作成できない環境ではテストをスキップする
            false
        }
}