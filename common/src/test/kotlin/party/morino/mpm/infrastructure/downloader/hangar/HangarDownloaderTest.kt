/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.downloader.hangar

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import party.morino.mpm.api.domain.downloader.model.RepositoryType
import party.morino.mpm.api.domain.downloader.model.TargetPlatform
import party.morino.mpm.api.domain.downloader.model.UrlData
import party.morino.mpm.api.domain.downloader.model.VersionData

class HangarDownloaderTest {
    // テスト用のHangarDownloaderインスタンス
    private val downloader = HangarDownloader(TargetPlatform.PAPER)

    /**
     * バージョン一覧APIをモックするダウンローダーを生成する
     *
     * @param platform ダウンローダーの動作プラットフォーム（フィクスチャは PAPER / WATERFALL のファイルだけを持つ）
     */
    private fun versionsMockDownloader(platform: TargetPlatform = TargetPlatform.PAPER): HangarDownloader {
        val mockEngine =
            MockEngine { request ->
                when {
                    // 単一バージョン取得（/versions/{name}）を先に判定する
                    Regex(".*/versions/[^?]+$").matches(request.url.encodedPath) -> {
                        respond(
                            content =
                                ByteReadChannel(
                                    party.morino.mpm.utils.MockDataLoader.Hangar
                                        .getVersionDetail()
                                ),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    request.url.encodedPath.endsWith("/versions") -> {
                        respond(
                            content =
                                ByteReadChannel(
                                    party.morino.mpm.utils.MockDataLoader.Hangar
                                        .getProjectVersions()
                                ),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    else -> respond(content = ByteReadChannel(""), status = HttpStatusCode.NotFound)
                }
            }
        return object : HangarDownloader(platform) {
            init {
                httpClient = HttpClient(mockEngine)
            }
        }
    }

    @Test
    @DisplayName("recognizes Hangar URL")
    fun getRepositoryType() {
        // HangarのURLが正しく認識されることをテスト
        val validUrl = "https://hangar.papermc.io/kennytv/Maintenance"
        assertEquals(RepositoryType.HANGAR, downloader.getRepositoryType(validUrl))

        // 無効なURLがnullを返すことをテスト
        assertNull(downloader.getRepositoryType("https://example.com/kennytv/Maintenance"))
    }

    @Test
    @DisplayName("extracts owner and project from URL")
    fun getUrlData() {
        val urlData = downloader.getUrlData("https://hangar.papermc.io/kennytv/Maintenance")

        assertNotNull(urlData)
        assertTrue(urlData is UrlData.HangarUrlData)
        val hangarUrlData = urlData as UrlData.HangarUrlData
        assertEquals("kennytv", hangarUrlData.owner)
        assertEquals("Maintenance", hangarUrlData.projectName)
    }

    @Test
    @DisplayName("latest version prefers Release channel")
    fun getLatestVersion() {
        val testDownloader = versionsMockDownloader()

        runBlocking {
            val urlData = UrlData.HangarUrlData("kennytv", "Maintenance")
            val versionData = testDownloader.getLatestVersion(urlData)

            // Betaを飛ばして最新のReleaseが選択される
            assertEquals("5.1.0", versionData.version)
            assertEquals("5.1.0", versionData.downloadId)
        }
    }

    @Test
    @DisplayName("tag filter selects matching channel")
    fun getLatestVersionByTag() {
        val testDownloader = versionsMockDownloader()

        runBlocking {
            val urlData = UrlData.HangarUrlData("kennytv", "Maintenance")

            // betaチャンネルの最新を取得
            val beta = testDownloader.getLatestVersionByTag(urlData, "beta")
            assertEquals("5.0.0-beta.2", beta?.version)

            // 存在しないチャンネルはnull
            assertNull(testDownloader.getLatestVersionByTag(urlData, "nonexistent"))
        }
    }

    @Test
    @DisplayName("getAllVersions returns every version")
    fun getAllVersions() {
        val testDownloader = versionsMockDownloader()

        runBlocking {
            val urlData = UrlData.HangarUrlData("kennytv", "Maintenance")
            val versions = testDownloader.getAllVersions(urlData)

            assertEquals(3, versions.size)
            assertEquals("5.1.0", versions[0].version)
        }
    }

    @Test
    @DisplayName("downloadByVersion prefers PAPER platform")
    fun downloadByVersion() {
        val testDownloader =
            object : HangarDownloader(TargetPlatform.PAPER) {
                init {
                    httpClient =
                        HttpClient(
                            MockEngine { request ->
                                when {
                                    request.url.toString().contains("/versions/5.1.0") &&
                                        request.url.host == "hangar.papermc.io" -> {
                                        respond(
                                            content =
                                                ByteReadChannel(
                                                    party.morino.mpm.utils.MockDataLoader.Hangar
                                                        .getVersionDetail()
                                                ),
                                            status = HttpStatusCode.OK,
                                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                                        )
                                    }

                                    request.url.host == "hangarcdn.papermc.io" -> {
                                        // version-detail.json の PAPER の sizeBytes と一致させ、
                                        // ダウンロードサイズ検証を通す
                                        respond(
                                            content = ByteReadChannel(ByteArray(233744)),
                                            status = HttpStatusCode.OK,
                                            headers = headersOf(HttpHeaders.ContentType, "application/java-archive")
                                        )
                                    }

                                    else -> respond(content = ByteReadChannel(""), status = HttpStatusCode.NotFound)
                                }
                            }
                        )
                }
            }

        runBlocking {
            val urlData = UrlData.HangarUrlData("kennytv", "Maintenance")
            val version = VersionData("5.1.0", "5.1.0")
            val file = testDownloader.downloadByVersion(urlData, version, null)

            assertNotNull(file)
            assertTrue(file!!.exists())
            file.delete()
        }
    }

    @Test
    @DisplayName("getVersionByName throws when not found")
    fun getVersionByNameNotFound() {
        val testDownloader = versionsMockDownloader()

        assertThrows<Exception> {
            runBlocking {
                val urlData = UrlData.HangarUrlData("kennytv", "Maintenance")
                testDownloader.getVersionByName(urlData, "9.9.9")
            }
        }
    }

    @Test
    @DisplayName("velocity ignores versions without a VELOCITY file")
    fun velocityIgnoresOtherPlatformVersions() {
        val testDownloader = versionsMockDownloader(TargetPlatform.VELOCITY)

        runBlocking {
            val urlData = UrlData.HangarUrlData("kennytv", "Maintenance")

            // フィクスチャのバージョンはすべて PAPER 向けのため、Velocity では一覧に出ず最新版も無い
            assertTrue(testDownloader.getAllVersions(urlData).isEmpty())
            assertThrows<Exception> { testDownloader.getLatestVersion(urlData) }
        }
    }

    @Test
    @DisplayName("velocity download does not fall back to another platform")
    fun velocityDownloadDoesNotFallBack() {
        val testDownloader = versionsMockDownloader(TargetPlatform.VELOCITY)

        runBlocking {
            val urlData = UrlData.HangarUrlData("kennytv", "Maintenance")
            val version = VersionData("5.1.0", "5.1.0")

            // VELOCITY のファイルが無いバージョンでは、PAPER のファイルを代わりにダウンロードせず失敗する
            val error = assertThrows<Exception> { testDownloader.downloadByVersion(urlData, version, null) }
            assertTrue(error.message!!.contains("VELOCITY"))
            // ハッシュもダウンロードと同じ選択に従い、他のプラットフォームのものを返さない
            assertNull(testDownloader.getVersionHashesByName(urlData, "5.1.0", null))
        }
    }

    @Test
    @DisplayName("velocity file name pattern does not match another platform")
    fun velocityPatternDoesNotMatchOtherPlatform() {
        val testDownloader = versionsMockDownloader(TargetPlatform.VELOCITY)

        runBlocking {
            val urlData = UrlData.HangarUrlData("kennytv", "Maintenance")
            val version = VersionData("5.1.0", "5.1.0")
            // どのファイル名にも一致するパターンでも、PAPER / WATERFALL のファイルは選ばれない
            val anyJar = ".*"

            assertNull(testDownloader.getVersionHashesByName(urlData, "5.1.0", anyJar))
            assertThrows<Exception> { testDownloader.downloadByVersion(urlData, version, anyJar) }
        }
    }
}