/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.cache

import arrow.core.Option
import arrow.core.none
import arrow.core.some
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.domain.cache.HttpMetadataCache
import party.morino.mpm.api.domain.config.ConfigManager
import party.morino.mpm.api.domain.config.PluginDirectory
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.logging.Logger

/**
 * リクエストURLをキーにしたTTLベースのHTTPメタデータキャッシュ実装
 *
 * plugins/mpm/cache/metadata/ 配下に「URLのSHA-256ハッシュ.json」という名前で
 * 1リクエスト1ファイルとして保存する。インデックスファイルを持たないため、
 * 並行ダウンロード時にインデックスの競合が起きない。
 */
class HttpMetadataCacheImpl :
    HttpMetadataCache,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()
    private val configManager: ConfigManager by inject()

    // JSONシリアライザ（キャッシュファイルの読み書きに使用）
    private val json = Json { ignoreUnknownKeys = true }

    private val logger: Logger = Logger.getLogger(HttpMetadataCacheImpl::class.java.name)

    /**
     * キャッシュされたレスポンス本文を取得する
     *
     * @param url リクエストURL（キャッシュキー）
     * @return 有効なキャッシュ本文、存在しない場合はNone
     */
    override fun get(url: String): Option<String> {
        val settings = configManager.getConfig().settings.cache
        // 無効化されている、またはTTLが0以下の場合はキャッシュを使用しない
        if (!settings.enabled || settings.metadataTtlSeconds <= 0) return none()

        val entryFile = entryFile(url)
        if (!entryFile.exists()) return none()

        val entry =
            try {
                json.decodeFromString(CachedMetadataEntry.serializer(), entryFile.readText())
            } catch (e: Exception) {
                // 壊れたエントリはミス扱いとし、次回以降のノイズにならないよう削除する
                logger.warning("キャッシュエントリの読み込みに失敗しました（${entryFile.name}）: ${e.message}")
                entryFile.delete()
                return none()
            }

        // ハッシュ衝突対策: 保存されたURLが一致しない場合はミス扱いにする
        if (entry.url != url) return none()

        // TTL超過判定（エントリ自体はcleanで削除されるまで残す）
        val ageSeconds = Instant.now().epochSecond - entry.fetchedAtEpochSeconds
        if (ageSeconds >= settings.metadataTtlSeconds) return none()

        return entry.body.some()
    }

    /**
     * レスポンス本文をキャッシュへ保存する
     *
     * 保存の失敗はログ警告のみに留め、呼び出し元のHTTP取得処理を失敗させない。
     *
     * @param url リクエストURL（キャッシュキー）
     * @param body 保存するレスポンス本文
     */
    override fun put(
        url: String,
        body: String
    ) {
        val settings = configManager.getConfig().settings.cache
        if (!settings.enabled || settings.metadataTtlSeconds <= 0) return

        try {
            val entry =
                CachedMetadataEntry(
                    url = url,
                    fetchedAtEpochSeconds = Instant.now().epochSecond,
                    body = body
                )
            entryFile(url).writeText(json.encodeToString(CachedMetadataEntry.serializer(), entry))
        } catch (e: Exception) {
            // キャッシュ書き込みの失敗はダウンロード処理を止める理由にならない
            logger.warning("キャッシュエントリの保存に失敗しました（$url）: ${e.message}")
        }
    }

    /**
     * 指定URLのキャッシュエントリを破棄する
     *
     * @param url リクエストURL（キャッシュキー）
     */
    override fun invalidate(url: String) {
        val entryFile = entryFile(url)
        if (entryFile.exists()) {
            entryFile.delete()
        }
    }

    /**
     * URLに対応するキャッシュファイルを返す
     *
     * @param url リクエストURL
     * @return キャッシュファイル（存在しない場合もある）
     */
    private fun entryFile(url: String): File = File(metadataDirectory(), "${cacheKey(url)}.json")

    /**
     * メタデータキャッシュのディレクトリを返す（存在しない場合は作成する）
     */
    private fun metadataDirectory(): File =
        File(pluginDirectory.getCacheDirectory(), CacheDirectories.METADATA).also { dir ->
            if (!dir.exists()) dir.mkdirs()
        }

    /**
     * URLからキャッシュキー（SHA-256の16進表現）を生成する
     *
     * @param url リクエストURL
     * @return キャッシュキー
     */
    private fun cacheKey(url: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}