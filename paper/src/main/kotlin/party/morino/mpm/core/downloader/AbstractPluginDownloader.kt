/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.core.downloader

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.get
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import party.morino.mpm.api.model.repository.PluginDownloader
import java.io.File

/**
 * プラグインダウンローダーの抽象クラス
 * 共通の機能を提供する
 */
abstract class AbstractPluginDownloader : PluginDownloader {
    // HTTP クライアント
    protected var httpClient =
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 60000
                connectTimeoutMillis = 60000
                socketTimeoutMillis = 60000
            }
        }

    // JSONパーサー
    protected val json = Json { ignoreUnknownKeys = true }

    /**
     * ファイルをダウンロードして一時ファイルとして保存
     * @param downloadUrl ダウンロードURL
     * @param fileName ファイル名
     * @return ダウンロードしたファイル
     */
    protected suspend fun downloadFile(
        downloadUrl: String,
        fileName: String
    ): File? =
        withContext(Dispatchers.IO) {
            try {
                val fileResponse =
                    httpClient.get(downloadUrl) {
                        headers {
                            append(HttpHeaders.Accept, "application/java-archive")
                            append(HttpHeaders.UserAgent, "MinecraftPluginManager")
                        }
                    }

                if (!fileResponse.status.isSuccess()) {
                    throw Exception("ファイルのダウンロードに失敗しました: ${fileResponse.status}")
                }

                val tempFile = File.createTempFile("plugin-", "-$fileName")
                tempFile.writeBytes(fileResponse.body())

                tempFile
            } catch (e: Exception) {
                println("プラグインのダウンロードに失敗しました: ${e.message}")
                null
            }
        }

    /**
     * HTTP GETリクエストを実行
     * @param url リクエストURL
     * @param acceptHeader Acceptヘッダーの値
     * @return レスポンスの本文
     */
    protected suspend fun getRequest(
        url: String,
        acceptHeader: String
    ): String =
        withContext(Dispatchers.IO) {
            val response =
                httpClient.get(url) {
                    headers {
                        append(HttpHeaders.Accept, acceptHeader)
                        append(HttpHeaders.UserAgent, "MinecraftPluginManager")
                    }
                }

            if (!response.status.isSuccess()) {
                throw Exception("リクエストが失敗しました: ${response.status}")
            }

            response.bodyAsText()
        }
}