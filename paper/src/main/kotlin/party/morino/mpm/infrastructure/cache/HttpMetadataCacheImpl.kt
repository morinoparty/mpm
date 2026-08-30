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
import party.morino.mpm.api.domain.config.model.CacheSettings
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
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

    // 退避処理の実行間隔を測るための保存回数カウンタ
    private val putCount = AtomicLong(0)

    companion object {
        // 何回保存するごとにディレクトリを走査して退避するか
        // （毎回全走査すると重いため、間隔を空けて償却する）
        // テストが退避の発火回数を決定的に再現できるようinternalで公開している
        internal const val CLEANUP_INTERVAL = 20L

        // キャッシュエントリの拡張子
        private const val ENTRY_EXTENSION = "json"
    }

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
            writeAtomically(entryFile(url), json.encodeToString(CachedMetadataEntry.serializer(), entry))
        } catch (e: Exception) {
            // キャッシュ書き込みの失敗はダウンロード処理を止める理由にならない
            logger.warning("キャッシュエントリの保存に失敗しました（$url）: ${e.message}")
        }

        // 一定回数ごとに期限切れ・超過分を退避し、キャッシュが際限なく増えないようにする
        if (putCount.incrementAndGet() % CLEANUP_INTERVAL == 0L) {
            evictOldEntries(settings)
        }
    }

    /**
     * エントリファイルをアトミックに置き換える
     *
     * `writeText`は既存ファイルを切り詰めてから書くため、書き込み中に[get]が読むと
     * 壊れたJSONとみなされて有効なエントリが削除されてしまう。
     * 同一ディレクトリ内の一時ファイルへ書いてからmoveすることでこれを避ける。
     *
     * @param target 置き換え先のエントリファイル
     * @param content 書き込む内容
     */
    private fun writeAtomically(
        target: File,
        content: String
    ) {
        val tempFile = File.createTempFile("entry-", ".tmp", target.parentFile)
        try {
            tempFile.writeText(content)
            try {
                Files.move(
                    tempFile.toPath(),
                    target.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (e: AtomicMoveNotSupportedException) {
                // アトミックな移動に対応しないファイルシステムでは通常の置き換えにフォールバックする
                logger.fine("アトミックな置き換えに対応していないため通常の移動を使用します: ${e.message}")
                Files.move(tempFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            // moveに成功していれば既に存在しないため、失敗時のみ実際に削除される
            tempFile.delete()
        }
    }

    /**
     * 期限切れエントリと上限を超えた古いエントリを削除する
     *
     * 本文を読まずにファイルの最終更新時刻で判定するため、走査コストが小さい。
     *
     * @param settings 現在のキャッシュ設定
     */
    private fun evictOldEntries(settings: CacheSettings) {
        try {
            val files = metadataDirectory().listFiles()?.filter { it.isFile } ?: return
            val ttlMillis = settings.metadataTtlSeconds * 1000
            val now = System.currentTimeMillis()

            // TTLを超過したエントリと、書き込み途中で残った一時ファイルを削除する
            val aliveEntries =
                files.filter { file ->
                    if (now - file.lastModified() >= ttlMillis) {
                        file.delete()
                        false
                    } else {
                        file.extension == ENTRY_EXTENSION
                    }
                }

            // エントリ数の上限を超えている場合は古い順に削除する
            val maxEntries = settings.maxMetadataEntries
            if (maxEntries > 0 && aliveEntries.size > maxEntries) {
                aliveEntries
                    .sortedBy { it.lastModified() }
                    .take(aliveEntries.size - maxEntries)
                    .forEach { it.delete() }
            }
        } catch (e: Exception) {
            // 退避の失敗はキャッシュの利用自体を妨げない
            logger.warning("キャッシュエントリの退避に失敗しました: ${e.message}")
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
    private fun entryFile(url: String): File = File(metadataDirectory(), "${cacheKey(url)}.$ENTRY_EXTENSION")

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