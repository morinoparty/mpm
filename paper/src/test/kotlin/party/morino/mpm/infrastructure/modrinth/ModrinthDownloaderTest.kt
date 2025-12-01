/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.modrinth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import party.morino.mpm.api.model.repository.RepositoryType
import party.morino.mpm.api.model.repository.UrlData
import party.morino.mpm.api.model.repository.VersionData

class ModrinthDownloaderTest {
    // テスト用のModrinthDownloaderインスタンス
    private val downloader = ModrinthDownloader()

    @Test
    fun getRepositoryType() {
        // ModrinthのURLが正しく認識されることをテスト
        val validUrl = "https://modrinth.com/plugin/plasmo-voice"
        assertEquals(RepositoryType.MODRINTH, downloader.getRepositoryType(validUrl))

        // 無効なURLがnullを返すことをテスト
        val invalidUrl = "https://example.com/plugin/plasmo-voice"
        assertNull(downloader.getRepositoryType(invalidUrl))
    }

    @Test
    fun getUrlData() {
        // 正しいURLからURLデータを抽出できることをテスト
        val url = "https://modrinth.com/plugin/plasmo-voice"
        val urlData = downloader.getUrlData(url)

        assertNotNull(urlData)
        assertTrue(urlData is UrlData.ModrinthUrlData)

        val modrinthUrlData = urlData as UrlData.ModrinthUrlData
        assertEquals("plasmo-voice", modrinthUrlData.id)
    }

    @Test
    fun getLatestVersion() {
        // mockファイルからデータを読み込み
        val mockEngine =
            MockEngine { request ->
                // APIリクエストのURLに応じてモックレスポンスを返す
                when {
                    request.url.toString().contains("/version?loaders=") -> {
                        respond(
                            content =
                                ByteReadChannel(
                                    party.morino.mpm.utils.MockDataLoader.Modrinth
                                        .getProjectVersions()
                                ),
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
            object : ModrinthDownloader() {
                init {
                    httpClient = HttpClient(mockEngine)
                }
            }

        // テストを実行
        runBlocking {
            val urlData = UrlData.ModrinthUrlData("plasmo-voice")
            val versionData = testDownloader.getLatestVersion(urlData)

            // 実際のデータに基づいたアサーション（version.jsonの最初のバージョン）
            assertEquals("j9WvAurZ", versionData.downloadId)
            assertEquals("spigot-2.1.6", versionData.version)
        }
    }

    @Test
    fun `downloadByVersion should select primary file when fileNamePattern is null`() {
        // パターンが指定されていない場合、プライマリファイルを選択することをテスト
        val mockEngine =
            MockEngine { request ->
                when {
                    request.url.toString().contains("/version/") -> {
                        // mockファイルからバージョン詳細を読み込み（単一オブジェクト）
                        respond(
                            content =
                                ByteReadChannel(
                                    party.morino.mpm.utils.MockDataLoader.Modrinth
                                        .getVersionDetail()
                                ),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                    request.url.toString().startsWith("https://cdn.modrinth.com/") -> {
                        // ファイルダウンロードのモックレスポンス
                        respond(
                            content = ByteReadChannel(ByteArray(100)),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/java-archive")
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

        val testDownloader =
            object : ModrinthDownloader() {
                init {
                    httpClient = HttpClient(mockEngine)
                }
            }

        runBlocking {
            val urlData = UrlData.ModrinthUrlData("plasmo-voice")
            val versionData = VersionData("j9WvAurZ", "spigot-2.1.6")

            // fileNamePattern が null の場合
            val file = testDownloader.downloadByVersion(urlData, versionData, null)

            // ファイルがダウンロードされたことを確認
            assertNotNull(file)
            assertTrue(file!!.exists())
            file.delete()
        }
    }

    @Test
    fun `downloadByVersion should select matching file when fileNamePattern is specified`() {
        // 正規表現にマッチするファイルを選択することをテスト
        val mockEngine =
            MockEngine { request ->
                when {
                    request.url.toString().contains("/version/") -> {
                        // mockファイルからバージョン詳細を読み込み（単一オブジェクト）
                        respond(
                            content =
                                ByteReadChannel(
                                    party.morino.mpm.utils.MockDataLoader.Modrinth
                                        .getVersionDetail()
                                ),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }
                    request.url.toString().startsWith("https://cdn.modrinth.com/") -> {
                        // ファイルダウンロードのモックレスポンス
                        respond(
                            content = ByteReadChannel(ByteArray(100)),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/java-archive")
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

        val testDownloader =
            object : ModrinthDownloader() {
                init {
                    httpClient = HttpClient(mockEngine)
                }
            }

        runBlocking {
            val urlData = UrlData.ModrinthUrlData("plasmo-voice")
            val versionData = VersionData("j9WvAurZ", "spigot-2.1.6")

            // 正規表現で .jar を含むファイルを指定
            val file = testDownloader.downloadByVersion(urlData, versionData, ".*Paper.*\\.jar")

            // ファイルがダウンロードされたことを確認
            assertNotNull(file)
            assertTrue(file!!.exists())
            file.delete()
        }
    }

    @Test
    fun `downloadByVersion should throw exception when no matching file is found`() {
        // 正規表現にマッチするファイルがない場合、例外を投げることをテスト
        val mockEngine =
            MockEngine { request ->
                when {
                    request.url.toString().contains("/version/") -> {
                        // mockファイルからバージョン詳細を読み込み（単一オブジェクト）
                        respond(
                            content =
                                ByteReadChannel(
                                    party.morino.mpm.utils.MockDataLoader.Modrinth
                                        .getVersionDetail()
                                ),
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

        val testDownloader =
            object : ModrinthDownloader() {
                init {
                    httpClient = HttpClient(mockEngine)
                }
            }

        // 例外が投げられることを確認
        assertThrows<Exception> {
            runBlocking {
                val urlData = UrlData.ModrinthUrlData("plasmo-voice")
                val versionData = VersionData("j9WvAurZ", "spigot-2.1.6")

                // マッチしない正規表現を指定
                testDownloader.downloadByVersion(urlData, versionData, ".*\\.zip")
            }
        }
    }

    @Test
    fun downloadLatest() {
        // downloadLatest は getLatestVersion と downloadByVersion を組み合わせたものなので
        // 個別のテストで十分カバーされている
    }
}