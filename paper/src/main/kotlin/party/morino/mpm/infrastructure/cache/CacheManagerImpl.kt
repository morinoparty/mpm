/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.cache

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.domain.cache.CacheManager
import party.morino.mpm.api.domain.config.ConfigManager
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.model.cache.CacheCleanResult
import party.morino.mpm.api.model.cache.CacheEntryInfo
import party.morino.mpm.api.model.cache.CacheSizeInfo
import party.morino.mpm.api.shared.error.MpmError
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.logging.Logger

/**
 * plugins/mpm/cache/ 配下のキャッシュを管理する実装クラス
 *
 * ServerBackupManagerImplと同様に「plugins/mpm配下の管理ディレクトリ」を走査する方式を取る。
 * メタデータキャッシュ以外のサブディレクトリ（将来のアーティファクトキャッシュ等）も
 * サイズ集計・削除の対象に含める。
 */
class CacheManagerImpl :
    CacheManager,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()
    private val configManager: ConfigManager by inject()

    // キャッシュファイルのデシリアライズに使用
    private val json = Json { ignoreUnknownKeys = true }

    private val logger: Logger = Logger.getLogger(CacheManagerImpl::class.java.name)

    // 取得日時の表示フォーマット（ローカルタイムゾーンのISO-8601）
    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault())

    /**
     * キャッシュエントリの一覧を取得する
     *
     * @return 成功時はCacheEntryInfoのリスト（取得日時の新しい順）、失敗時はMpmError.CacheError
     */
    override fun list(): Either<MpmError, List<CacheEntryInfo>> =
        try {
            val ttlSeconds =
                configManager
                    .getConfig()
                    .settings.cache.metadataTtlSeconds
            val now = Instant.now().epochSecond

            val entries =
                metadataFiles().map { file ->
                    val entry = decodeEntry(file)
                    if (entry == null) {
                        // デコードできないエントリも「掃除できる対象」として可視化する
                        CacheEntryInfo(
                            key = file.nameWithoutExtension,
                            url = file.name,
                            sizeBytes = file.length(),
                            fetchedAt = timestampFormatter.format(Instant.ofEpochMilli(file.lastModified())),
                            expired = true
                        )
                    } else {
                        CacheEntryInfo(
                            key = file.nameWithoutExtension,
                            url = entry.url,
                            sizeBytes = file.length(),
                            fetchedAt = timestampFormatter.format(Instant.ofEpochSecond(entry.fetchedAtEpochSeconds)),
                            expired = isExpired(entry.fetchedAtEpochSeconds, now, ttlSeconds)
                        )
                    }
                }

            entries.sortedByDescending { it.fetchedAt }.right()
        } catch (e: Exception) {
            MpmError.CacheError.Failed("キャッシュ一覧の取得に失敗しました: ${e.message}").left()
        }

    /**
     * キャッシュディレクトリ全体のサイズ情報を取得する
     *
     * @return 成功時はCacheSizeInfo、失敗時はMpmError.CacheError
     */
    override fun size(): Either<MpmError, CacheSizeInfo> =
        try {
            // メタデータ以外のサブディレクトリも含めて集計する
            val allFiles = cacheFiles()
            val ttlSeconds =
                configManager
                    .getConfig()
                    .settings.cache.metadataTtlSeconds
            val now = Instant.now().epochSecond

            val expiredCount =
                metadataFiles().count { file ->
                    val entry = decodeEntry(file)
                    entry == null || isExpired(entry.fetchedAtEpochSeconds, now, ttlSeconds)
                }

            CacheSizeInfo(
                totalSizeBytes = allFiles.sumOf { it.length() },
                entryCount = allFiles.size,
                expiredEntryCount = expiredCount
            ).right()
        } catch (e: Exception) {
            MpmError.CacheError.Failed("キャッシュサイズの計算に失敗しました: ${e.message}").left()
        }

    /**
     * キャッシュエントリを削除する
     *
     * @param expiredOnly trueの場合はTTLを超過したメタデータエントリのみを削除する
     * @return 成功時はCacheCleanResult、失敗時はMpmError.CacheError
     */
    override fun clean(expiredOnly: Boolean): Either<MpmError, CacheCleanResult> =
        try {
            val ttlSeconds =
                configManager
                    .getConfig()
                    .settings.cache.metadataTtlSeconds
            val now = Instant.now().epochSecond

            // 削除対象を決定する（expiredOnlyの場合はTTL超過・破損エントリのみ）
            val targets =
                if (expiredOnly) {
                    metadataFiles().filter { file ->
                        val entry = decodeEntry(file)
                        entry == null || isExpired(entry.fetchedAtEpochSeconds, now, ttlSeconds)
                    }
                } else {
                    cacheFiles()
                }

            var removedEntries = 0
            var freedBytes = 0L
            for (file in targets) {
                val length = file.length()
                if (file.delete()) {
                    removedEntries++
                    freedBytes += length
                } else {
                    logger.warning("キャッシュファイルの削除に失敗しました: ${file.absolutePath}")
                }
            }

            CacheCleanResult(removedEntries = removedEntries, freedBytes = freedBytes).right()
        } catch (e: Exception) {
            MpmError.CacheError.Failed("キャッシュの削除に失敗しました: ${e.message}").left()
        }

    /**
     * メタデータキャッシュのエントリファイル一覧を返す
     *
     * metadataディレクトリ自体がシンボリックリンクの場合はキャッシュ外のJSONを指しうるため、
     * 対象から外す（--expired経由で外部ファイルを削除しないようにするため）。
     */
    private fun metadataFiles(): List<File> {
        val root = cacheRoot()
        val metadataDir = File(root, CacheDirectories.METADATA)
        if (Files.isSymbolicLink(metadataDir.toPath())) {
            logger.warning(
                "メタデータキャッシュディレクトリがシンボリックリンクのため処理をスキップしました: ${metadataDir.absolutePath}"
            )
            return emptyList()
        }
        if (!metadataDir.isDirectory) return emptyList()
        return metadataDir
            .listFiles()
            // File.isFileはリンクを辿るため、シンボリックリンクは明示的に除外する
            ?.filter {
                it.isFile &&
                    it.extension == "json" &&
                    !Files.isSymbolicLink(it.toPath()) &&
                    isInsideCache(it, root)
            } ?: emptyList()
    }

    /**
     * キャッシュディレクトリのcanonicalパスを返す
     *
     * キャッシュディレクトリ自体をシンボリックリンクにする運用は管理者の意図なので、
     * 解決後のパスを境界の基準にする。
     */
    private fun cacheRoot(): File = pluginDirectory.getCacheDirectory().canonicalFile

    /**
     * キャッシュディレクトリ配下の通常ファイルを列挙する
     *
     * File.walkTopDownはシンボリックリンク先のディレクトリも辿るため、
     * `cache/link -> /srv/data` のようなリンクがあると外部のファイルまで削除対象になる。
     * これを避けるためリンクを辿らないFiles.walkFileTreeを使い、
     * さらにcanonicalパスがキャッシュディレクトリ配下かを検証する（二重の防御）。
     * なお、シンボリックリンクそのものは削除もサイズ集計もしない。
     */
    private fun cacheFiles(): List<File> {
        val root = cacheRoot()
        // 走査対象が無い場合はwalkFileTreeが例外を投げるため先に返す
        if (!root.isDirectory) return emptyList()

        val files = mutableListOf<File>()
        // FOLLOW_LINKSを渡さないwalkFileTreeはシンボリックリンクを辿らない
        Files.walkFileTree(
            root.toPath(),
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(
                    file: Path,
                    attrs: BasicFileAttributes
                ): FileVisitResult {
                    // リンクを辿らない属性なので、シンボリックリンクはisRegularFileがfalseになる
                    if (attrs.isRegularFile) {
                        val candidate = file.toFile()
                        if (isInsideCache(candidate, root)) files.add(candidate)
                    }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(
                    file: Path,
                    exc: IOException
                ): FileVisitResult {
                    // 読み取れないファイルは無視して走査を続ける
                    logger.warning("キャッシュファイルの走査に失敗しました: $file")
                    return FileVisitResult.CONTINUE
                }
            }
        )
        return files
    }

    /**
     * 対象ファイルがキャッシュディレクトリのcanonicalパス配下にあるかを判定する
     *
     * @param file 判定対象のファイル
     * @param root キャッシュディレクトリ（canonical済み）
     */
    private fun isInsideCache(
        file: File,
        root: File
    ): Boolean =
        try {
            file.canonicalPath.startsWith(root.canonicalPath + File.separator)
        } catch (e: IOException) {
            // パスを解決できない場合は安全側に倒して対象外にする
            logger.warning("キャッシュファイルのパス解決に失敗しました: ${file.absolutePath}")
            false
        }

    /**
     * キャッシュファイルをデコードする（失敗時はnull）
     */
    private fun decodeEntry(file: File): CachedMetadataEntry? =
        try {
            json.decodeFromString(CachedMetadataEntry.serializer(), file.readText())
        } catch (e: Exception) {
            null
        }

    /**
     * TTLを超過しているかどうかを判定する
     *
     * TTLが0以下の場合はキャッシュ自体が無効なので、全エントリを期限切れとみなす。
     */
    private fun isExpired(
        fetchedAtEpochSeconds: Long,
        nowEpochSeconds: Long,
        ttlSeconds: Long
    ): Boolean = ttlSeconds <= 0 || nowEpochSeconds - fetchedAtEpochSeconds >= ttlSeconds
}