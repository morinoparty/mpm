/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.downloader

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.get
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.context.GlobalContext
import party.morino.mpm.api.domain.cache.HttpMetadataCache
import party.morino.mpm.api.domain.downloader.PluginDownloader
import party.morino.mpm.api.shared.error.MpmError
import java.io.Closeable
import java.io.File
import java.time.Instant
import java.util.logging.Logger

/**
 * プラグインダウンローダーの抽象クラス
 * 共通の機能を提供する
 */
abstract class AbstractPluginDownloader :
    PluginDownloader,
    Closeable {
    // HTTP クライアント（テストのためにopenかつ変更可能）
    protected open var httpClient: HttpClient = buildHttpClient()

    // JSONパーサー
    protected val json = Json { ignoreUnknownKeys = true }

    // エラーログ出力用（サーバーのログ設定/レベルに従わせるためprintlnではなくLoggerを使用）
    private val logger: Logger = Logger.getLogger(this::class.java.name)

    /**
     * 上流APIへのリクエストが認証済みかどうか
     *
     * レート制限に当たったとき「トークンが効いていないせいなのか」を切り分けるために使う。
     * 認証を持つダウンローダー（GitHub等）がoverrideする
     */
    protected open val authenticated: Boolean get() = false

    companion object {
        // 一時的な障害（5xx / 429 / ネットワークエラー）に対するリトライ回数
        private const val MAX_RETRIES = 3

        // レート制限を示すHTTPステータスコード
        private const val TOO_MANY_REQUESTS = 429

        // GitHubは一次・二次レート制限の超過を403で返す（429ではない）
        private const val FORBIDDEN = 403

        // トークンが無効な場合に返るステータスコード
        private const val UNAUTHORIZED = 401

        // 個々の通信（接続確立 / 無応答）に対するタイムアウト（ミリ秒）
        private const val TIMEOUT_MILLIS = 60_000L
    }

    /**
     * ダウンローダー共通設定を適用したHTTPクライアントを生成する
     *
     * リトライ設定はプロダクションと同じ経路でテストできるよう、
     * エンジンを差し替えられる形にしている（テストからはMockEngineを渡す）。
     *
     * 認証ヘッダーなどダウンローダー固有の設定は[additionalConfig]で追加する。
     * 各ダウンローダーがHttpClientを手書きするとリトライ設定が失われるため、
     * 生成経路は必ずこのメソッドに集約する。
     *
     * @param engine 使用するHTTPエンジン。nullの場合はCIOエンジンを使用する
     * @param additionalConfig 共通設定の後に適用する追加設定
     * @return 設定済みのHttpClient
     */
    protected fun buildHttpClient(
        engine: HttpClientEngine? = null,
        additionalConfig: HttpClientConfig<*>.() -> Unit = {}
    ): HttpClient =
        if (engine == null) {
            HttpClient(CIO) {
                configureCommonPlugins()
                additionalConfig()
            }
        } else {
            HttpClient(engine) {
                configureCommonPlugins()
                additionalConfig()
            }
        }

    /**
     * タイムアウトとリトライの共通設定をHTTPクライアントへ適用する
     *
     * 上流API（GitHub / Modrinth / Hangar / Spiget）は一時的な5xxやレート制限（429）を返すため、
     * 指数バックオフでリトライし、429の`Retry-After`ヘッダーを尊重する。
     */
    private fun HttpClientConfig<*>.configureCommonPlugins() {
        install(HttpTimeout) {
            // requestTimeoutMillisはリトライのバックオフを含むリクエスト全体に掛かるため、
            // 有効にすると`Retry-After: 60`のようなレート制限応答でリトライ前に打ち切られてしまう。
            // また大きなjarのダウンロードが遅い回線で中断される原因にもなるため無効化し、
            // 接続確立と無応答の検出はconnect/socketタイムアウトで行う。
            requestTimeoutMillis = HttpTimeoutConfig.INFINITE_TIMEOUT_MS
            connectTimeoutMillis = TIMEOUT_MILLIS
            socketTimeoutMillis = TIMEOUT_MILLIS
        }

        install(HttpRequestRetry) {
            // サーバーエラー(5xx)とレート制限(429)をリトライ対象にする。
            // retryOnServerErrorsと同じ内部状態（shouldRetry）を設定するため、
            // 429を含めたこのretryIfのみを指定する。
            retryIf(maxRetries = MAX_RETRIES) { _, response ->
                // GitHubは二次レート制限を403 + `retry-after` で返すため、
                // 待ち時間の指示がある403はリトライ対象に含める。
                // `retry-after` の無い403は権限不足などの恒久的な拒否なのでリトライしない
                val retryableForbidden =
                    response.status.value == FORBIDDEN && response.headers["retry-after"] != null
                response.status.value >= HttpStatusCode.InternalServerError.value ||
                    response.status.value == TOO_MANY_REQUESTS ||
                    retryableForbidden
            }
            // ネットワーク断やタイムアウトなどの例外もリトライする
            retryOnException(maxRetries = MAX_RETRIES, retryOnTimeout = true)
            // 指数バックオフ（429/503のRetry-Afterヘッダーが存在する場合はそちらを優先する）
            exponentialDelay(respectRetryAfterHeader = true)
        }
    }

    /**
     * ファイルをダウンロードして一時ファイルとして保存する
     *
     * レスポンスの検証を行い、HTMLのエラーページや途中で切れたレスポンスを
     * jarとして保存してしまわないようにする。
     *
     * @param downloadUrl ダウンロードURL
     * @param fileName ファイル名
     * @param expectedSizeBytes 期待するファイルサイズ（バイト）。不明な場合はnullまたは0以下を渡す
     * @return 成功時はダウンロードしたファイル、失敗時は型付きのMpmError.DownloadError
     */
    protected suspend fun downloadFile(
        downloadUrl: String,
        fileName: String,
        expectedSizeBytes: Long? = null
    ): Either<MpmError.DownloadError, File> =
        withContext(Dispatchers.IO) {
            try {
                val fileResponse =
                    httpClient.get(downloadUrl) {
                        headers {
                            append(HttpHeaders.Accept, "application/java-archive")
                            append(HttpHeaders.UserAgent, "mpm")
                        }
                    }

                // リトライを尽くしても成功しなかった場合はステータスを添えて失敗させる
                if (!fileResponse.status.isSuccess()) {
                    return@withContext MpmError.DownloadError
                        .HttpStatus(downloadUrl, fileResponse.status.value)
                        .left()
                }

                // HTMLが返された場合はjarではなくエラーページ（メンテナンス画面やログイン要求）とみなす。
                // Content-Typeが無い場合は判定できないため通す。
                val contentType = fileResponse.contentType()
                if (contentType != null && contentType.match(ContentType.Text.Html)) {
                    return@withContext MpmError.DownloadError
                        .InvalidContentType(downloadUrl, contentType.toString())
                        .left()
                }

                // ストリーミングでファイルに書き込み（メモリに全体をロードしない）
                val tempFile = File.createTempFile("plugin-", "-$fileName")
                val writtenBytes =
                    try {
                        val channel = fileResponse.bodyAsChannel()
                        tempFile.outputStream().use { output ->
                            channel.toInputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                    } catch (e: Exception) {
                        // ストリーミング中に失敗した場合、不完全な一時ファイルを残さないよう削除する
                        tempFile.delete()
                        throw e
                    }

                // 期待サイズが分かっている場合は実際に書き込んだバイト数と比較する
                // （途中で切れたレスポンスが200で返るケースを検出する）
                if (expectedSizeBytes != null && expectedSizeBytes > 0 && writtenBytes != expectedSizeBytes) {
                    tempFile.delete()
                    return@withContext MpmError.DownloadError
                        .SizeMismatch(downloadUrl, expectedSizeBytes, writtenBytes)
                        .left()
                }

                tempFile.right()
            } catch (e: Exception) {
                // 原因を握りつぶさず、型付きエラーとして呼び出し側へ返す
                logger.warning("プラグインのダウンロードに失敗しました: ${e.message}")
                MpmError.DownloadError.Failed(downloadUrl, e.message ?: e::class.java.name).left()
            }
        }

    /**
     * ファイルをダウンロードし、失敗時は[PluginDownloadException]を投げる
     *
     * [PluginDownloader]のダウンロードAPIは戻り値が`File?`のため、
     * 失敗理由を返せない。nullで握りつぶす代わりに例外へ載せて伝播させる。
     *
     * @param downloadUrl ダウンロードURL
     * @param fileName ファイル名
     * @param expectedSizeBytes 期待するファイルサイズ（バイト）。不明な場合はnull
     * @return ダウンロードしたファイル
     * @throws PluginDownloadException ダウンロードまたは検証に失敗した場合
     */
    protected suspend fun downloadFileOrThrow(
        downloadUrl: String,
        fileName: String,
        expectedSizeBytes: Long? = null
    ): File =
        downloadFile(downloadUrl, fileName, expectedSizeBytes).fold(
            { error -> throw PluginDownloadException(error) },
            { file -> file }
        )

    /**
     * HTTP GETリクエストを実行する
     *
     * メタデータ（バージョン一覧など）は同一セッション中に繰り返し取得されるため、
     * TTL内であればキャッシュから返してネットワーク往復を短絡させる。
     *
     * @param url リクエストURL
     * @param acceptHeader Acceptヘッダーの値
     * @return レスポンスの本文
     */
    protected suspend fun getRequest(
        url: String,
        acceptHeader: String
    ): String {
        // キャッシュヒットした場合はネットワークへ出ない
        cachedMetadata(url)?.let { return it }

        return withContext(Dispatchers.IO) {
            val response =
                httpClient.get(url) {
                    headers {
                        append(HttpHeaders.Accept, acceptHeader)
                        append(HttpHeaders.UserAgent, "mpm")
                    }
                }

            if (!response.status.isSuccess()) {
                // 401/403/429は原因が複数あり（未認証のレート制限 / 二次レート制限 /
                // トークンの権限不足 / 組織のPAT制限）、HTTPコードだけでは区別できない。
                // 上流が返す理由をログに残さないと利用者が手掛かりを得られないため、
                // ここでメッセージとレート制限ヘッダーを記録する
                logUpstreamRejection(url, response)
                throw PluginDownloadException(MpmError.DownloadError.HttpStatus(url, response.status.value))
            }

            val body = response.bodyAsText()
            // 成功レスポンスのみキャッシュする
            storeMetadata(url, body)
            body
        }
    }

    /**
     * キャッシュされたメタデータを取得する
     *
     * ダウンローダーはテストからKoinを起動せずに生成されることがあるため、
     * `by inject()`ではなくGlobalContextから任意取得する。
     * キャッシュが利用できない場合は素通し（null）となる。
     *
     * @param url リクエストURL
     * @return キャッシュされた本文、存在しない場合はnull
     */
    private fun cachedMetadata(url: String): String? =
        metadataCache()?.let { cache ->
            // キャッシュ層の障害でメタデータ取得自体を失敗させない
            runCatching { cache.get(url).getOrNull() }.getOrNull()
        }

    /**
     * メタデータをキャッシュへ保存する（ベストエフォート）
     *
     * @param url リクエストURL
     * @param body レスポンス本文
     */
    private fun storeMetadata(
        url: String,
        body: String
    ) {
        metadataCache()?.let { cache ->
            runCatching { cache.put(url, body) }
        }
    }

    /**
     * 認証・レート制限に関わる拒否（401/403/429）の理由をログへ残す
     *
     * 403は次のいずれでも返るため、ステータスコードだけでは原因を切り分けられない。
     * 上流のメッセージとレート制限ヘッダーを出して判断できるようにする。
     *
     * - 未認証のままレート制限（GitHubは60リクエスト/時）を超えた
     * - 短時間に集中したリクエストによる二次レート制限（`retry-after` が付く）
     * - トークンの権限不足、または組織がそのトークン種別を許可していない
     *
     * @param url 失敗したリクエストのURL
     * @param response 失敗レスポンス
     */
    private suspend fun logUpstreamRejection(
        url: String,
        response: HttpResponse
    ) {
        val status = response.status.value
        if (status != UNAUTHORIZED && status != FORBIDDEN && status != TOO_MANY_REQUESTS) return

        val authState = if (authenticated) "認証済み" else "未認証"
        val remaining = response.headers["x-ratelimit-remaining"]
        val limit = response.headers["x-ratelimit-limit"]
        val retryAfter = response.headers["retry-after"]
        // GitHubは不足している権限をこのヘッダーで教えてくれる（例: contents=read）。
        // fine-grained PATに対象リポジトリの権限が無い場合の切り分けに使う
        val requiredPermissions = response.headers["x-accepted-github-permissions"]
        val resetAt =
            response.headers["x-ratelimit-reset"]
                ?.toLongOrNull()
                ?.let { Instant.ofEpochSecond(it).toString() }

        // 上流のエラー本文は短いJSON。原因が直接書かれているので取り出して出す
        val upstreamMessage =
            runCatching {
                json
                    .parseToJsonElement(response.bodyAsText())
                    .jsonObject["message"]
                    ?.jsonPrimitive
                    ?.content
            }.getOrNull()

        val details =
            listOfNotNull(
                authState,
                limit?.let { "limit=$it" },
                remaining?.let { "remaining=$it" },
                resetAt?.let { "reset=$it" },
                retryAfter?.let { "retry-after=${it}s" },
                requiredPermissions?.let { "required-permissions=$it" }
            ).joinToString(", ")

        logger.warning("上流APIがHTTP $status で拒否しました（$details）: $url${upstreamMessage?.let { " / $it" } ?: ""}")

        // 未認証でレート制限を使い切った場合は、対処方法まで案内する
        if (!authenticated && remaining == "0") {
            logger.warning(
                "config.json の settings.githubToken にPersonal Access Tokenを設定すると" +
                    "GitHub APIの上限が60→5000リクエスト/時になります。設定後は `/mpm reload` で反映されます"
            )
            return
        }

        // 認証済みでレート制限にも余裕があるのに拒否された場合はトークンの権限不足。
        // fine-grained PATは対象リポジトリを明示的に選ばないと公開リポジトリでも403になる
        if (authenticated && requiredPermissions != null && remaining != "0") {
            logger.warning(
                "トークンに必要な権限（$requiredPermissions）がありません。" +
                    "fine-grained PAT（github_pat_で始まるもの）の場合は対象リポジトリを選んで権限を付与するか、" +
                    "公開リポジトリのみを扱うならスコープ無しのclassicトークンを使用してください"
            )
        }
    }

    /**
     * Koinに登録されているメタデータキャッシュを取得する（未登録・未起動時はnull）
     */
    private fun metadataCache(): HttpMetadataCache? = GlobalContext.getOrNull()?.getOrNull<HttpMetadataCache>()

    /**
     * HTTPクライアントを閉じてリソースを解放する
     * プラグイン無効化時に呼び出し、コネクション/セレクタスレッドの
     * リークを防ぐ
     */
    override fun close() {
        httpClient.close()
    }
}