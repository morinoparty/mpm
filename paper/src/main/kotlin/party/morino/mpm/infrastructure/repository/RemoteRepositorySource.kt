/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.repository

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.domain.repository.PluginRepositorySource
import party.morino.mpm.api.domain.repository.RepositoryFile
import party.morino.mpm.infrastructure.repository.graph.RepositoryGraphResolver
import party.morino.mpm.infrastructure.repository.graph.RepositoryIndexFetcher
import party.morino.mpm.infrastructure.repository.graph.ResolvedRepositoryGraph
import java.io.Closeable

/**
 * リモートのインデックス（index.json）からリポジトリグラフをたどってカタログを取得するソース
 *
 * ルートのindexを取得し、その children を [RepositoryGraphResolver] で再帰的に読み取って
 * 1つのカタログ（プラグイン名 -> 定義）に合成する。リポジトリ側にサーバーロジックは不要で、
 * 静的ファイルとしてホスティングされたindexだけで動作する。
 *
 * 探索結果は一定時間キャッシュする。[RepositoryManagerImpl] は `mpm add` 1回で
 * [isAvailable] と [getRepositoryFile] を複数回呼ぶため、毎回グラフをたどらないようにする。
 *
 * @property url インデックスのURL。`.json` で終わらない場合は末尾に `/index.json` を補う
 * @property headers ルートのリクエストにだけ付与するHTTPヘッダー（認証トークン等）。
 *   第三者がホストする子リポジトリには転送しない。
 *   なおHTTPリダイレクトは追わないため、`url` には最終的なURLを指定する
 */
class RemoteRepositorySource(
    private val url: String,
    private val headers: Map<String, String> = emptyMap()
) : PluginRepositorySource,
    Closeable,
    KoinComponent {
    // ログ出力用（KoinによるDI）
    private val plugin: JavaPlugin by inject()

    // HTTPクライアント（テストのためにリフレクションで差し替え可能）
    private var httpClient: HttpClient =
        HttpClient(CIO) {
            // リダイレクトを追わない。子リポジトリのURLは isSafeChildUrl で検証しているが、
            // 追従を許すと安全なホストから内部アドレスへ 3xx で飛ばされて検証を迂回できてしまう。
            // ルートは管理者が設定するURLなので、最終的なURLを直接指定してもらう
            followRedirects = false
            install(HttpTimeout) {
                // タイムアウトを30秒に設定
                requestTimeoutMillis = 30000
                connectTimeoutMillis = 30000
                socketTimeoutMillis = 30000
            }
        }

    // ルートのインデックスURL（設定値がディレクトリ形式なら index.json を補う）
    private val indexUrl: String =
        if (url.endsWith(".json")) url else "${url.trimEnd('/')}/index.json"

    // 探索結果のキャッシュ。null は「ルートに到達できなかった」ことを表す（ネガティブキャッシュ）
    @Volatile
    private var cachedGraph: ResolvedRepositoryGraph? = null

    @Volatile
    private var cacheExpiresAt: Long = 0

    // 同時に複数のcoroutineがキャッシュ切れを検知しても探索を1回に絞る
    private val resolveMutex = Mutex()

    companion object {
        // プラグイン名として許可する文字パターン（英数字・ハイフン・アンダースコア）
        private val PLUGIN_NAME_PATTERN = Regex("^[A-Za-z0-9_-]+$")

        // 探索結果のキャッシュ寿命（RepositoryManagerImpl の一覧キャッシュと揃える）
        private const val CACHE_TTL_MILLIS = 180_000L

        // ルートに到達できなかったときに再試行を抑える時間
        private const val FAILURE_TTL_MILLIS = 60_000L

        // 1つのインデックスとして受け付ける最大サイズ（Content-Lengthは信用せず実読で計数する）
        private const val MAX_INDEX_BYTES = 1024 * 1024

        // 子リポジトリ1件あたりの取得タイムアウト。
        // 探索全体は resolveMutex の下で直列に走るため、応答しない子1件で
        // すべてのコマンドが長時間止まらないようルートより短くする
        private const val CHILD_REQUEST_TIMEOUT_MILLIS = 10_000L
    }

    /**
     * リモートソースが利用可能かを確認
     * ルートのインデックスが取得・解析できれば利用可能とみなす
     * @return 利用可能な場合はtrue
     */
    override suspend fun isAvailable(): Boolean = resolveGraph() != null

    /**
     * 利用可能なプラグインの一覧を取得
     * グラフ全体（子リポジトリ含む）から集めたプラグイン名を返す
     * @return プラグイン名のリスト
     */
    override suspend fun getAvailablePlugins(): List<String> = resolveGraph()?.plugins?.keys?.sorted() ?: emptyList()

    /**
     * 指定したプラグインのリポジトリファイルを取得
     * @param pluginName プラグイン名
     * @return リポジトリファイルの内容、見つからない場合はnull
     */
    override suspend fun getRepositoryFile(pluginName: String): RepositoryFile? {
        // グラフ上のキーは安全な文字のみで構成されるため、それ以外の名前は探すまでもなく不在
        if (!PLUGIN_NAME_PATTERN.matches(pluginName)) {
            plugin.logger.warning("不正なプラグイン名が指定されたため、リポジトリファイルの取得をスキップしました: $pluginName")
            return null
        }
        return resolveGraph()?.plugins?.get(pluginName)
    }

    /**
     * キャッシュが有効ならそれを返し、切れていればグラフを探索し直す
     * @return 探索結果。ルートに到達できない場合はnull
     */
    private suspend fun resolveGraph(): ResolvedRepositoryGraph? {
        val now = System.currentTimeMillis()
        if (now < cacheExpiresAt) return cachedGraph

        return resolveMutex.withLock {
            // ロック待ちの間に別のcoroutineが更新していれば再利用する
            val latest = System.currentTimeMillis()
            if (latest < cacheExpiresAt) return@withLock cachedGraph

            val resolver = RepositoryGraphResolver(RepositoryIndexFetcher { fetchIndex(it) })
            val graph = resolver.resolve(indexUrl)

            if (graph == null) {
                plugin.logger.warning("リモートリポジトリのインデックスを取得できませんでした: $indexUrl")
            } else {
                graph.warnings.forEach { plugin.logger.warning("リモートリポジトリ ($indexUrl): $it") }
            }

            cachedGraph = graph
            cacheExpiresAt = latest + if (graph == null) FAILURE_TTL_MILLIS else CACHE_TTL_MILLIS
            graph
        }
    }

    /**
     * インデックスをHTTPで取得する
     *
     * カスタムヘッダーはルートのURLに対してのみ付与する。子リポジトリは第三者がホストするため、
     * 認証トークン等を転送しない。
     * @param target 取得するインデックスのURL
     * @return レスポンス本文。到達不能・非2xx・サイズ超過の場合はnull
     */
    private suspend fun fetchIndex(target: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val isRoot = target == indexUrl
                val response =
                    httpClient.get(target) {
                        headers {
                            append(HttpHeaders.Accept, "application/json")
                            append(HttpHeaders.UserAgent, "mpm")
                            if (isRoot) {
                                this@RemoteRepositorySource.headers.forEach { (key, value) -> append(key, value) }
                            }
                        }
                        if (!isRoot) {
                            timeout { requestTimeoutMillis = CHILD_REQUEST_TIMEOUT_MILLIS }
                        }
                    }

                if (!response.status.isSuccess()) {
                    return@withContext null
                }

                readBodyWithLimit(response)
            } catch (e: Exception) {
                // 原因を診断できるようログに記録してnullを返す
                plugin.logger.warning("リモートリポジトリのインデックス取得に失敗しました: ${e.message} ($target)")
                null
            }
        }

    /**
     * レスポンス本文を上限付きで読み取る
     * @return 本文。上限を超えた場合はnull
     */
    private suspend fun readBodyWithLimit(response: HttpResponse): String? {
        val declared = response.contentLength()
        if (declared != null && declared > MAX_INDEX_BYTES) return null

        val buffer = java.io.ByteArrayOutputStream()
        response.bodyAsChannel().toInputStream().use { input ->
            val chunk = ByteArray(8 * 1024)
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                // 上限を1バイトでも超えたら読み続けずに打ち切る
                if (buffer.size() + read > MAX_INDEX_BYTES) return null
                buffer.write(chunk, 0, read)
            }
        }
        return buffer.toString(Charsets.UTF_8.name())
    }

    /**
     * リポジトリソースの種類を取得
     * @return "remote"
     */
    override fun getSourceType(): String = "remote"

    /**
     * リポジトリソースの識別子を取得
     * @return 設定されたURL
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