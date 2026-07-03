/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.repository

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.domain.repository.PluginRepositorySource
import party.morino.mpm.api.domain.repository.RepositoryFile
import java.io.Closeable

/**
 * リモートURLからリポジトリファイルを取得するソース
 * @property url リポジトリのベースURL
 * @property headers HTTPリクエストに追加するヘッダー
 */
class RemoteRepositorySource(
    private val url: String,
    private val headers: Map<String, String> = emptyMap()
) : PluginRepositorySource,
    Closeable,
    KoinComponent {
    // ログ出力用（KoinによるDI）
    private val plugin: JavaPlugin by inject()

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

    companion object {
        // プラグイン名として許可する文字パターン（英数字・ハイフン・アンダースコア）
        private val PLUGIN_NAME_PATTERN = Regex("^[A-Za-z0-9_-]+$")
    }

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
                    httpClient.head("${url.trimEnd('/')}/list") {
                        headers {
                            append(HttpHeaders.UserAgent, "mpm")
                            // カスタムヘッダーを追加
                            this@RemoteRepositorySource.headers.forEach { (key, value) ->
                                append(key, value)
                            }
                        }
                    }

                // レスポンスステータスが成功の場合はtrue
                response.status.isSuccess()
            } catch (e: Exception) {
                // タイムアウトやエラーの場合はfalseを返しつつ、原因を診断できるようログに記録する
                plugin.logger.warning("リモートリポジトリソースへの接続確認に失敗しました: ${e.message} ($url)")
                false
            }
        }

    /**
     * 利用可能なプラグインの一覧を取得
     * {url}/list にアクセスしてプラグイン一覧を取得
     * @return プラグイン名のリスト
     */
    override suspend fun getAvailablePlugins(): List<String> =
        withContext(Dispatchers.IO) {
            try {
                // {url}/list にGETリクエストを送信
                val indexUrl = "${url.trimEnd('/')}/list"
                val response =
                    httpClient.get(indexUrl) {
                        headers {
                            append(HttpHeaders.Accept, "application/json")
                            append(HttpHeaders.UserAgent, "mpm")
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
                // エラーの場合は空のリストを返しつつ、原因を診断できるようログに記録する
                plugin.logger.warning("リモートリポジトリソースからのプラグイン一覧取得に失敗しました: ${e.message} ($url)")
                emptyList()
            }
        }

    /**
     * 指定したプラグインのリポジトリファイルを取得
     * {url}/plugins/{pluginName}.json にGETリクエストを送信
     * @param pluginName プラグイン名
     * @return リポジトリファイルの内容、見つからない場合はnull
     */
    override suspend fun getRepositoryFile(pluginName: String): RepositoryFile? =
        withContext(Dispatchers.IO) {
            // パストラバーサル対策: 英数字・ハイフン・アンダースコア以外の文字を含む
            // プラグイン名はURLパスの意図しないセグメントへ抜け出す恐れがあるため拒否する
            if (!isValidPluginName(pluginName)) {
                plugin.logger.warning("不正なプラグイン名が指定されたため、リポジトリファイルの取得をスキップしました: $pluginName")
                return@withContext null
            }

            try {
                // {url}/plugins/{pluginName}.json にGETリクエストを送信
                val fileUrl = "${url.trimEnd('/')}/plugins/$pluginName.json"
                val response =
                    httpClient.get(fileUrl) {
                        headers {
                            append(HttpHeaders.Accept, "application/json")
                            append(HttpHeaders.UserAgent, "mpm")
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
                // エラーの場合はnullを返しつつ、原因を診断できるようログに記録する
                plugin.logger.warning("リモートリポジトリソースからのリポジトリファイル取得に失敗しました: ${e.message} (プラグイン: $pluginName, URL: $url)")
                null
            }
        }

    /**
     * プラグイン名がURLパスセグメントとして安全かどうかを検証する
     * "/" や ".." を含む名前を許可すると、意図した /plugins/ プレフィックスから
     * 外れたパスへリクエストが送られてしまう（パストラバーサル）ため、
     * 英数字・ハイフン・アンダースコアのみで構成される名前だけを許可する
     * @param pluginName 検証対象のプラグイン名
     * @return 安全な名前であればtrue
     */
    private fun isValidPluginName(pluginName: String): Boolean = PLUGIN_NAME_PATTERN.matches(pluginName)

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

    /**
     * HTTPクライアントを閉じてリソースを解放する
     * reloadやプラグイン無効化時に呼び出し、コネクション/セレクタスレッドの
     * リークを防ぐ
     */
    override fun close() {
        httpClient.close()
    }
}