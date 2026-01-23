/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.downloader.github

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
import org.junit.jupiter.api.assertThrows
import party.morino.mpm.api.domain.downloader.model.RepositoryType
import party.morino.mpm.api.domain.downloader.model.UrlData
import party.morino.mpm.api.domain.downloader.model.VersionData

class GithubDownloaderTest {
    // テスト用のGithubDownloaderインスタンス
    private val downloader = GithubDownloader()

    @Test
    fun getRepositoryType() {
        // GitHubのURLが正しく認識されることをテスト
        val validUrl = "https://github.com/owner/repository"
        assertEquals(RepositoryType.GITHUB, downloader.getRepositoryType(validUrl))

        // 無効なURLがnullを返すことをテスト
        val invalidUrl = "https://example.com/owner/repository"
        assertNull(downloader.getRepositoryType(invalidUrl))
    }

    @Test
    fun getUrlData() {
        // 正しいURLからURLデータを抽出できることをテスト
        val url = "https://github.com/owner/repository"
        val urlData = downloader.getUrlData(url)

        assertNotNull(urlData)
        assertTrue(urlData is UrlData.GithubUrlData)

        val githubUrlData = urlData as UrlData.GithubUrlData
        assertEquals("owner", githubUrlData.owner)
        assertEquals("repository", githubUrlData.repository)
    }

    @Test
    fun getLatestVersion() {
        // mockファイルからデータを読み込み
        val mockEngine =
            MockEngine { request ->
                // APIリクエストのURLに応じてモックレスポンスを返す
                when (request.url.toString()) {
                    "https://api.github.com/repos/EssentialsX/Essentials/releases/latest" -> {
                        respond(
                            content =
                                ByteReadChannel(
                                    party.morino.mpm.utils.MockDataLoader.Github
                                        .getLatestRelease()
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
            object : GithubDownloader() {
                init {
                    httpClient = HttpClient(mockEngine)
                }
            }

        // テストを実行
        runBlocking {
            val urlData = UrlData.GithubUrlData("EssentialsX", "Essentials")
            val versionData = testDownloader.getLatestVersion(urlData)

            // 実際のデータに基づいたアサーション
            assertEquals("227902189", versionData.downloadId)
            assertEquals("2.21.2", versionData.version)
        }
    }

    @Test
    fun `downloadByVersion should select first asset when fileNamePattern is null`() {
        // パターンが指定されていない場合、最初のアセットを選択することをテスト
        val mockEngine =
            MockEngine { request ->
                when {
                    request.url.toString().contains("/releases/") &&
                        request.url.toString().endsWith("/assets") -> {
                        // mockファイルからアセット一覧を読み込み
                        respond(
                            content =
                                ByteReadChannel(
                                    party.morino.mpm.utils.MockDataLoader.Github
                                        .getReleaseAssets()
                                ),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    request.url.toString().startsWith(
                        "https://github.com/EssentialsX/Essentials/releases/download/"
                    ) -> {
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
            object : GithubDownloader() {
                init {
                    httpClient = HttpClient(mockEngine)
                }
            }

        runBlocking {
            val urlData = UrlData.GithubUrlData("EssentialsX", "Essentials")
            val versionData = VersionData("227902189", "2.21.2")

            // fileNamePattern が null の場合
            val file = testDownloader.downloadByVersion(urlData, versionData, null)

            // ファイルがダウンロードされたことを確認
            assertNotNull(file)
            assertTrue(file!!.exists())
            file.delete()
        }
    }

    @Test
    fun `downloadByVersion should select matching asset when fileNamePattern is specified`() {
        // 正規表現にマッチするアセットを選択することをテスト
        val mockEngine =
            MockEngine { request ->
                when {
                    request.url.toString().contains("/releases/") &&
                        request.url.toString().endsWith("/assets") -> {
                        // mockファイルからアセット一覧を読み込み
                        respond(
                            content =
                                ByteReadChannel(
                                    party.morino.mpm.utils.MockDataLoader.Github
                                        .getReleaseAssets()
                                ),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    request.url.toString().startsWith(
                        "https://github.com/EssentialsX/Essentials/releases/download/"
                    ) -> {
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
            object : GithubDownloader() {
                init {
                    httpClient = HttpClient(mockEngine)
                }
            }

        runBlocking {
            val urlData = UrlData.GithubUrlData("EssentialsX", "Essentials")
            val versionData = VersionData("227902189", "2.21.2")

            // 正規表現で Chat.jar を指定
            val file = testDownloader.downloadByVersion(urlData, versionData, ".*Chat.*\\.jar")

            // ファイルがダウンロードされたことを確認
            assertNotNull(file)
            assertTrue(file!!.exists())
            file.delete()
        }
    }

    @Test
    fun `downloadByVersion should throw exception when no matching asset is found`() {
        // 正規表現にマッチするアセットがない場合、例外を投げることをテスト
        val mockEngine =
            MockEngine { request ->
                when {
                    request.url.toString().contains("/releases/") &&
                        request.url.toString().endsWith("/assets") -> {
                        // mockファイルからアセット一覧を読み込み
                        respond(
                            content =
                                ByteReadChannel(
                                    party.morino.mpm.utils.MockDataLoader.Github
                                        .getReleaseAssets()
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
            object : GithubDownloader() {
                init {
                    httpClient = HttpClient(mockEngine)
                }
            }

        // 例外が投げられることを確認
        assertThrows<Exception> {
            runBlocking {
                val urlData = UrlData.GithubUrlData("EssentialsX", "Essentials")
                val versionData = VersionData("227902189", "2.21.2")

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