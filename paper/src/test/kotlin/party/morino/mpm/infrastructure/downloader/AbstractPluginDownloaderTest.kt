/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.downloader

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.shared.error.MpmError
import party.morino.mpm.infrastructure.downloader.spigot.SpigotDownloader

/**
 * AbstractPluginDownloaderのダウンロード検証とリトライ設定を検証するテスト
 *
 * 具象クラスとしてSpigotDownloaderを利用し、protectedなdownloadFileを
 * 匿名サブクラスから公開してテストする。
 */
@DisplayName("AbstractPluginDownloader - download validation and retry")
class AbstractPluginDownloaderTest {
    private val downloadUrl = "https://example.com/plugin.jar"

    @Test
    @DisplayName("downloadFile rejects an HTML error page")
    fun rejectsHtmlResponse() {
        val mockEngine =
            MockEngine {
                // 200でHTMLのエラーページが返ってくるケース
                respond(
                    content = ByteReadChannel("<html><body>error</body></html>"),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/html; charset=utf-8")
                )
            }

        val downloader =
            object : SpigotDownloader() {
                init {
                    httpClient = HttpClient(mockEngine)
                }

                suspend fun download() = downloadFile(downloadUrl, "plugin.jar")
            }

        runBlocking {
            val result = downloader.download()

            assertTrue(result.isLeft())
            assertInstanceOf(MpmError.DownloadError.InvalidContentType::class.java, result.leftOrNull())
        }
    }

    @Test
    @DisplayName("downloadFile rejects a truncated body")
    fun rejectsTruncatedResponse() {
        val mockEngine =
            MockEngine {
                // 期待サイズより短いボディが200で返ってくるケース
                respond(
                    content = ByteReadChannel(ByteArray(100)),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/java-archive")
                )
            }

        val downloader =
            object : SpigotDownloader() {
                init {
                    httpClient = HttpClient(mockEngine)
                }

                suspend fun download() = downloadFile(downloadUrl, "plugin.jar", expectedSizeBytes = 500L)
            }

        runBlocking {
            val result = downloader.download()

            val error = result.leftOrNull()
            assertInstanceOf(MpmError.DownloadError.SizeMismatch::class.java, error)
            assertEquals(100L, (error as MpmError.DownloadError.SizeMismatch).actualBytes)
        }
    }

    @Test
    @DisplayName("http client retries a 429 response")
    fun retriesRateLimitedResponse() {
        var attempts = 0
        val mockEngine =
            MockEngine {
                attempts++
                if (attempts == 1) {
                    // 1回目はレート制限。Retry-Afterを0秒にしてテストを長引かせない
                    respond(
                        content = ByteReadChannel(""),
                        status = HttpStatusCode.TooManyRequests,
                        headers = headersOf(HttpHeaders.RetryAfter, "0")
                    )
                } else {
                    respond(
                        content = ByteReadChannel(ByteArray(10)),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/java-archive")
                    )
                }
            }

        val downloader =
            object : SpigotDownloader() {
                init {
                    // プロダクションと同じリトライ設定を通してテストする
                    httpClient = buildHttpClient(mockEngine)
                }

                suspend fun download() = downloadFile(downloadUrl, "plugin.jar")
            }

        runBlocking {
            val result = downloader.download()

            assertTrue(result.isRight())
            // 429の後にリトライされ、2回目で成功していること
            assertEquals(2, attempts)
            result.getOrNull()?.delete()
        }
    }
}