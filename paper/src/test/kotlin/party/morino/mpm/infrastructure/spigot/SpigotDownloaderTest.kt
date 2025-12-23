/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.spigot

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import party.morino.mpm.api.model.repository.RepositoryType
import party.morino.mpm.api.model.repository.UrlData
import party.morino.mpm.api.model.repository.VersionData
import party.morino.mpm.utils.MockDataLoader

class SpigotDownloaderTest {
    // テスト用のSpigotDownloaderインスタンス
    private val downloader = SpigotDownloader()

    @Test
    fun getRepositoryType() {
        // SpigotMCのURLが正しく認識されることをテスト
        val validUrl = "https://www.spigotmc.org/resources/essentialsx.9089/"
        assertEquals(RepositoryType.SPIGOTMC, downloader.getRepositoryType(validUrl))

        // 無効なURLがnullを返すことをテスト
        val invalidUrl = "https://example.com/resources/test.1234/"
        assertNull(downloader.getRepositoryType(invalidUrl))
    }

    @Test
    fun getUrlData() {
        // 正しいURLからURLデータを抽出できることをテスト
        val url = "https://www.spigotmc.org/resources/essentialsx.9089/"
        val urlData = downloader.getUrlData(url)

        assertNotNull(urlData)
        assertTrue(urlData is UrlData.SpigotMcUrlData)

        val spigotUrlData = urlData as UrlData.SpigotMcUrlData
        assertEquals("9089", spigotUrlData.resourceId)
    }

    @Test
    fun getLatestVersion() {
        // mockファイルからデータを読み込み
        val mockEngine =
            MockEngine { request ->
                when (request.url.toString()) {
                    "https://api.spiget.org/v2/resources/9089/versions?sort=-name&size=1" -> {
                        respond(
                            content = ByteReadChannel(MockDataLoader.Spigot.getLatestVersion()),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    else -> {
                        respond(
                            content = ByteReadChannel(""),
                            status = HttpStatusCode.NotFound
                        )
                    }
                }
            }

        // モックエンジンを使用するダウンローダーを作成
        val testDownloader =
            object : SpigotDownloader() {
                init {
                    httpClient = HttpClient(mockEngine)
                }
            }

        // テストを実行
        runBlocking {
            val urlData = UrlData.SpigotMcUrlData("9089")
            val versionData = testDownloader.getLatestVersion(urlData)

            // 実際のmockデータに基づいたアサーション
            assertEquals("96357", versionData.downloadId)
            assertEquals("201-b354", versionData.version)
        }
    }

    @Test
    fun downloadByVersion() {
        // mockファイルからデータを読み込み
        val mockEngine =
            MockEngine { request ->
                when {
                    request.url.toString().startsWith("https://api.spiget.org/v2/resources/9089/versions/") &&
                        request.url.toString().endsWith("/download/proxy") -> {
                        // ファイルダウンロードのモックレスポンス
                        respond(
                            content = ByteReadChannel(ByteArray(100)),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/java-archive")
                        )
                    }

                    request.url.toString() == "https://api.spiget.org/v2/resources/9089" -> {
                        // リソース詳細のモックレスポンス
                        respond(
                            content = ByteReadChannel(MockDataLoader.Spigot.getResourceDetails()),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    else -> {
                        respond(
                            content = ByteReadChannel(""),
                            status = HttpStatusCode.NotFound
                        )
                    }
                }
            }

        // モックエンジンを使用するダウンローダーを作成
        val testDownloader =
            object : SpigotDownloader() {
                init {
                    httpClient = HttpClient(mockEngine)
                }
            }

        // テストを実行
        runBlocking {
            val urlData = UrlData.SpigotMcUrlData("9089")
            val versionData = VersionData("96357", "201-b354")

            val file = testDownloader.downloadByVersion(urlData, versionData, null)

            // ファイルがダウンロードされたことを確認
            assertNotNull(file)
            assertTrue(file!!.exists())
            file.delete()
        }
    }
}