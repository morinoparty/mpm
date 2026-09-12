/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.domain.downloader.DownloaderRepository
import party.morino.mpm.api.domain.downloader.model.RepositoryType
import party.morino.mpm.api.domain.downloader.model.UrlData
import party.morino.mpm.api.domain.downloader.model.VersionData
import party.morino.mpm.api.domain.repository.ChannelConfig
import party.morino.mpm.api.domain.repository.RepositoryConfig
import java.io.File

@DisplayName("ChannelVersionResolver - channel config must not silently fall back")
class ChannelVersionResolverTest {
    private val urlData = UrlData.SpigotMcUrlData("34315")

    /**
     * 必要な3メソッドだけを差し替えるフェイク。
     * 「フィルタ無しの最新」と「全バージョン一覧」を別物として観測できるようにしてある。
     */
    private class FakeDownloader(
        private val all: List<VersionData> = emptyList(),
        private val unfilteredLatest: VersionData = VersionData("134421", "Version 1.6.6"),
        private val throwOnAll: Boolean = false
    ) : DownloaderRepository {
        override fun getRepositoryType(url: String): RepositoryType? = RepositoryType.SPIGOTMC

        override fun getUrlData(url: String): UrlData? = null

        override suspend fun getLatestVersion(urlData: UrlData): VersionData = unfilteredLatest

        override suspend fun getAllVersions(urlData: UrlData): List<VersionData> {
            if (throwOnAll) throw IllegalStateException("upstream down")
            return all
        }

        override suspend fun getVersionByName(
            urlData: UrlData,
            versionName: String
        ): VersionData = VersionData("unused", versionName)

        override suspend fun downloadByVersion(
            urlData: UrlData,
            version: VersionData,
            fileNamePattern: String?
        ): File? = null

        override suspend fun downloadLatest(
            url: String,
            fileNamePattern: String?
        ): File? = null

        override suspend fun searchPlugins(
            query: String,
            limit: Int
        ) = emptyList<party.morino.mpm.api.domain.downloader.model.PluginSearchResult>()
    }

    private fun configWithMatcher(matcher: String) =
        RepositoryConfig(
            type = "spigotmc",
            repositoryId = "34315",
            latest = ChannelConfig(versionMatcher = matcher)
        )

    @Test
    @DisplayName("a matching version is resolved through the matcher")
    fun testMatcherResolves() {
        runBlocking {
            val downloader =
                FakeDownloader(
                    all = listOf(VersionData("344916", "1.7.3"), VersionData("134421", "Version 1.6.6"))
                )
            val result =
                ChannelVersionResolver.resolveLatest(
                    downloader,
                    urlData,
                    configWithMatcher("^\\d+\\.\\d+\\.\\d+$")
                )
            assertEquals("1.7.3", result.version)
        }
    }

    @Test
    @DisplayName("a matcher with no match does not fall back to the unfiltered latest")
    fun testNoMatchDoesNotFallBack() {
        runBlocking {
            // Vault の事象の再現: 一覧に条件を満たすものが無いとき、
            // 以前はフィルタ無しの "Version 1.6.6" を最新として返し、ダウングレードを提案していた
            val downloader = FakeDownloader(all = listOf(VersionData("134421", "Version 1.6.6")))
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    ChannelVersionResolver.resolveLatest(
                        downloader,
                        urlData,
                        configWithMatcher("^\\d+\\.\\d+\\.\\d+$")
                    )
                }
            }
        }
    }

    @Test
    @DisplayName("an upstream failure does not fall back to the unfiltered latest")
    fun testUpstreamFailureDoesNotFallBack() {
        runBlocking {
            val downloader = FakeDownloader(throwOnAll = true)
            assertThrows(IllegalStateException::class.java) {
                runBlocking {
                    ChannelVersionResolver.resolveLatest(
                        downloader,
                        urlData,
                        configWithMatcher("^\\d+\\.\\d+\\.\\d+$")
                    )
                }
            }
        }
    }

    @Test
    @DisplayName("no channel config still falls back to the platform default")
    fun testNotConfiguredFallsBack() {
        runBlocking {
            // 解決方法が設定されていないときだけ、従来どおりプラットフォーム既定へ委ねる
            val downloader = FakeDownloader()
            val result = ChannelVersionResolver.resolveLatest(downloader, urlData, null)
            assertEquals("Version 1.6.6", result.version)

            val withoutChannel = RepositoryConfig(type = "spigotmc", repositoryId = "34315")
            assertEquals(
                "Version 1.6.6",
                ChannelVersionResolver.resolveLatest(downloader, urlData, withoutChannel).version
            )
        }
    }

    @Test
    @DisplayName("a tag with no match resolves to null instead of the platform default")
    fun testTagNoMatchIsNull() {
        runBlocking {
            val downloader = FakeDownloader(all = listOf(VersionData("1", "1.0.0")))
            val config =
                RepositoryConfig(
                    type = "spigotmc",
                    repositoryId = "34315",
                    beta = ChannelConfig(versionMatcher = "-beta$")
                )
            assertNull(ChannelVersionResolver.resolveTag(downloader, urlData, config, "beta"))
        }
    }
}