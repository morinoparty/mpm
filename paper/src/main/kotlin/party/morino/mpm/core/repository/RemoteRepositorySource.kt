/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.core.repository

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import party.morino.mpm.api.core.repository.PluginRepositorySource
import party.morino.mpm.api.core.repository.RepositoryFile

/**
 * リモートURLからリポジトリファイルを取得するソース
 * @property url リポジトリのベースURL
 * @property headers HTTPリクエストに追加するヘッダー
 */
class RemoteRepositorySource(
    private val url: String,
    private val headers: Map<String, String> = emptyMap()
) : PluginRepositorySource {
    // HTTPクライアント（テストのためにopenかつ変更可能）
    private var httpClient: HttpClient =
        HttpClient(CIO) {
            install(HttpTimeout) {
                // タイムアウトを30秒に設定
                requestTimeoutMillis = 30000
                connectTimeoutMillis = 30000
                socketTimeoutMillis = 30000
            }
        }

    // JSONパーサー
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * リモートソースが利用可能かを確認
     * ベースURLにHEADリクエストを送信してレスポンスを確認
     * @return 利用可能な場合はtrue
     */
    override suspend fun isAvailable(): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // ベースURLにHEADリクエストを送信
                val response =
                    httpClient.head("${url.trimEnd('/')}/_list.json") {
                        headers {
                            append(HttpHeaders.UserAgent, "MinecraftPluginManager")
                            // カスタムヘッダーを追加
                            this@RemoteRepositorySource.headers.forEach { (key, value) ->
                                append(key, value)
                            }
                        }
                    }

                // レスポンスステータスが成功の場合はtrue
                response.status.isSuccess()
            } catch (e: Exception) {
                // タイムアウトやエラーの場合はfalse
                false
            }
        }

    /**
     * 利用可能なプラグインの一覧を取得
     * {url}/index.json にアクセスしてプラグイン一覧を取得
     * @return プラグイン名のリスト
     */
    override suspend fun getAvailablePlugins(): List<String> =
        withContext(Dispatchers.IO) {
            try {
                // {url}/index.json にGETリクエストを送信
                val indexUrl = "${url.trimEnd('/')}/_list.json"
                val response =
                    httpClient.get(indexUrl) {
                        headers {
                            append(HttpHeaders.Accept, "application/json")
                            append(HttpHeaders.UserAgent, "MinecraftPluginManager")
                            // カスタムヘッダーを追加
                            this@RemoteRepositorySource.headers.forEach { (key, value) ->
                                append(key, value)
                            }
                        }
                    }

                // レスポンスが成功でない場合は空のリストを返す
                if (!response.status.isSuccess()) {
                    return@withContext emptyList()
                }

                // レスポンスをJSON配列としてデシリアライズ
                val responseText: String = response.body()
                json.decodeFromString<List<String>>(responseText)
            } catch (e: Exception) {
                // エラーの場合は空のリストを返す
                emptyList()
            }
        }

    /**
     * 指定したプラグインのリポジトリファイルを取得
     * {url}/{pluginName}.json にGETリクエストを送信
     * @param pluginName プラグイン名
     * @return リポジトリファイルの内容、見つからない場合はnull
     */
    override suspend fun getRepositoryFile(pluginName: String): RepositoryFile? =
        withContext(Dispatchers.IO) {
            try {
                // {url}/{pluginName}.json にGETリクエストを送信
                val fileUrl = "${url.trimEnd('/')}/plugins/$pluginName.json"
                val response =
                    httpClient.get(fileUrl) {
                        headers {
                            append(HttpHeaders.Accept, "application/json")
                            append(HttpHeaders.UserAgent, "MinecraftPluginManager")
                            // カスタムヘッダーを追加
                            this@RemoteRepositorySource.headers.forEach { (key, value) ->
                                append(key, value)
                            }
                        }
                    }

                // レスポンスが成功でない場合はnullを返す
                if (!response.status.isSuccess()) {
                    return@withContext null
                }

                // レスポンスをRepositoryFileにデシリアライズ
                val responseText: String = response.body()
                json.decodeFromString<RepositoryFile>(responseText)
            } catch (e: Exception) {
                // エラーの場合はnullを返す
                null
            }
        }

    /**
     * リポジトリソースの種類を取得
     * @return "remote"
     */
    override fun getSourceType(): String = "remote"

    /**
     * リポジトリソースの識別子を取得
     * @return ベースURL
     */
    override fun getIdentifier(): String = url
}