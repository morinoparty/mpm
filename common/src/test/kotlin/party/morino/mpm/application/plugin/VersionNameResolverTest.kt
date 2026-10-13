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
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.domain.downloader.DownloaderRepository
import party.morino.mpm.api.domain.downloader.model.RepositoryType
import party.morino.mpm.api.domain.downloader.model.UrlData
import party.morino.mpm.api.domain.downloader.model.VersionData
import party.morino.mpm.api.shared.error.MpmError
import java.io.File

@DisplayName("VersionNameResolver - a normalized version must still reach the real tag")
class VersionNameResolverTest {
    private val urlData = UrlData.GithubUrlData("morinoparty", "MineAuth")

    /**
     * GitHub の挙動を最小限に真似たフェイク。
     * `getVersionByName` は実タグと完全一致したときだけ成功し、それ以外は 404 相当で例外を投げる。
     * 一覧取得の回数を数えておき、raw タグ指定で余計な往復が発生しないことを検証できるようにしてある。
     */
    private class FakeDownloader(
        private val releases: List<VersionData> = emptyList(),
        private val throwOnAll: Boolean = false
    ) : DownloaderRepository {
        var getAllVersionsCalls = 0
            private set

        override fun getRepositoryType(url: String): RepositoryType? = RepositoryType.GITHUB

        override fun getUrlData(url: String): UrlData? = null

        override suspend fun getLatestVersion(urlData: UrlData): VersionData = releases.first()

        override suspend fun getAllVersions(urlData: UrlData): List<VersionData> {
            getAllVersionsCalls++
            if (throwOnAll) throw IllegalStateException("upstream down")
            return releases
        }

        override suspend fun getVersionByName(
            urlData: UrlData,
            versionName: String
        ): VersionData =
            releases.firstOrNull { it.version == versionName }
                ?: throw Exception("バージョン '$versionName' が見つかりませんでした: HTTP 404")

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

    private val releases = listOf(VersionData("2001", "v0.3.9"), VersionData("2000", "v0.3.8"))

    @Test
    @DisplayName("a raw tag resolves without listing versions")
    fun testRawTagSkipsListing() {
        runBlocking {
            val downloader = FakeDownloader(releases)
            val result = VersionNameResolver.resolve(downloader, urlData, "MineAuth", "v0.3.9")
            assertEquals("2001", result.getOrNull()?.downloadId)
            // raw タグはこの1リクエストで解決できるので、一覧取得のコストを払ってはならない
            assertEquals(0, downloader.getAllVersionsCalls)
        }
    }

    @Test
    @DisplayName("a normalized version falls back to the release list")
    fun testNormalizedVersionFallsBack() {
        runBlocking {
            // issue #442 の再現: mpm.json に "0.3.9" と書かれていても実タグ "v0.3.9" に解決する
            val downloader = FakeDownloader(releases)
            val result = VersionNameResolver.resolve(downloader, urlData, "MineAuth", "0.3.9")
            assertEquals("v0.3.9", result.getOrNull()?.version)
            assertEquals("2001", result.getOrNull()?.downloadId)
        }
    }

    @Test
    @DisplayName("an unknown version reports a resolution failure")
    fun testUnknownVersionIsResolutionFailure() {
        runBlocking {
            val downloader = FakeDownloader(releases)
            val result = VersionNameResolver.resolve(downloader, urlData, "MineAuth", "9.9.9")
            assertInstanceOf(MpmError.PluginError.VersionResolutionFailed::class.java, result.leftOrNull())
        }
    }

    @Test
    @DisplayName("an upstream listing failure is reported as unavailable")
    fun testUpstreamFailureIsUnavailable() {
        runBlocking {
            // 一時障害を「指定バージョンが存在しない」と伝えてしまわないことを確認する
            val downloader = FakeDownloader(releases, throwOnAll = true)
            val result = VersionNameResolver.resolve(downloader, urlData, "MineAuth", "0.3.9")
            assertInstanceOf(MpmError.PluginError.UpstreamUnavailable::class.java, result.leftOrNull())
        }
    }
}