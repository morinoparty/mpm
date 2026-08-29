/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
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
import org.koin.core.context.GlobalContext
import party.morino.mpm.api.domain.cache.HttpMetadataCache
import party.morino.mpm.api.domain.downloader.PluginDownloader
import party.morino.mpm.api.shared.error.MpmError
import java.io.Closeable
import java.io.File
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

    companion object {
        // 一時的な障害（5xx / 429 / ネットワークエラー）に対するリトライ回数
        private const val MAX_RETRIES = 3

        // レート制限を示すHTTPステータスコード
        private const val TOO_MANY_REQUESTS = 429

        // タイムアウト（ミリ秒）
        private const val TIMEOUT_MILLIS = 60_000L
    }

    /**
     * ダウンローダー共通設定を適用したHTTPクライアントを生成する
     *
     * リトライ設定はプロダクションと同じ経路でテストできるよう、
     * エンジンを差し替えられる形にしている（テストからはMockEngineを渡す）。
     *
     * @param engine 使用するHTTPエンジン。nullの場合はCIOエンジンを使用する
     * @return 設定済みのHttpClient
     */
    protected fun buildHttpClient(engine: HttpClientEngine? = null): HttpClient =
        if (engine == null) {
            HttpClient(CIO) { configureCommonPlugins() }
        } else {
            HttpClient(engine) { configureCommonPlugins() }
        }

    /**
     * タイムアウトとリトライの共通設定をHTTPクライアントへ適用する
     *
     * 上流API（GitHub / Modrinth / Hangar / Spiget）は一時的な5xxやレート制限（429）を返すため、
     * 指数バックオフでリトライし、429の`Retry-After`ヘッダーを尊重する。
     */
    private fun HttpClientConfig<*>.configureCommonPlugins() {
        install(HttpTimeout) {
            requestTimeoutMillis = TIMEOUT_MILLIS
            connectTimeoutMillis = TIMEOUT_MILLIS
            socketTimeoutMillis = TIMEOUT_MILLIS
        }

        install(HttpRequestRetry) {
            // サーバーエラー(5xx)とレート制限(429)をリトライ対象にする。
            // retryOnServerErrorsと同じ内部状態（shouldRetry）を設定するため、
            // 429を含めたこのretryIfのみを指定する。
            retryIf(maxRetries = MAX_RETRIES) { _, response ->
                response.status.value >= HttpStatusCode.InternalServerError.value ||
                    response.status.value == TOO_MANY_REQUESTS
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