/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.plugin.retire

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.application.plugin.DeferredJarDeletion
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.infrastructure.migration.AtomicFileWriter
import party.morino.mpm.utils.Utils
import party.morino.mpm.utils.isSameFile
import java.io.File

/**
 * 削除予約を `plugins/mpm/pending-delete.json` に永続化する [DeferredJarDeletion] の実装
 *
 * 予約はファイル名（plugins ディレクトリ直下）で保持し、
 * 削除時は plugins ディレクトリに対して解決する。
 */
class DeferredJarDeletionImpl :
    DeferredJarDeletion,
    KoinComponent {
    // Koinによる依存性注入
    private val plugin: JavaPlugin by inject()
    private val pluginDirectory: PluginDirectory by inject()

    // 予約ファイルの読み書きが競合しないようにする
    private val lock = Any()

    /**
     * 予約ファイルの場所（plugins/mpm/pending-delete.json）
     */
    private val pendingFile: File
        get() = File(pluginDirectory.getRootDirectory(), PENDING_FILE_NAME)

    override fun schedule(jarFile: File): Either<String, Unit> =
        synchronized(lock) {
            val current = load()
            // 同じファイルを二重に予約しない
            if (jarFile.name in current.files) return Unit.right()
            save(current.copy(files = current.files + jarFile.name))
        }

    override fun cancel(jarFile: File) {
        synchronized(lock) {
            val current = load()
            if (jarFile.name !in current.files) return
            save(current.copy(files = current.files - jarFile.name)).onLeft { plugin.logger.warning(it) }
        }
    }

    override fun isScheduled(jarFile: File): Boolean = synchronized(lock) { jarFile.name in load().files }

    override fun deleteScheduled(): List<File> =
        synchronized(lock) {
            val pluginsDir = pluginDirectory.getPluginsDirectory()
            val (deleted, remaining) = deletePending(load().files, pluginsDir)
            deleted.forEach { plugin.logger.info("削除予約されていた ${it.name} を削除しました") }
            remaining.forEach { plugin.logger.warning("削除予約されていた $it を削除できませんでした。次回起動時に再試行します") }
            save(PendingJarDeletions(remaining)).onLeft { plugin.logger.warning(it) }
            deleted
        }

    override fun cleanupOnStartup(runningJar: File) {
        synchronized(lock) {
            val pending = load().files
            if (pending.isEmpty()) return

            val pluginsDir = pluginDirectory.getPluginsDirectory()
            // Paper が「削除予約された旧JAR」の方を読み込んでしまった場合は、自分自身なので削除しない
            val (self, others) = pending.partition { isSameFile(File(pluginsDir, it), runningJar) }
            self.forEach { name ->
                plugin.logger.warning(
                    "実行中の $name は削除予約された旧JARです。新しい mpm のJARが plugins/ に並んでいるはずなので、" +
                        "$name を手動で削除してからサーバーを再起動してください"
                )
            }

            val (deleted, remaining) = deletePending(others, pluginsDir)
            deleted.forEach { plugin.logger.info("前回削除しきれなかった ${it.name} を削除しました") }
            remaining.forEach { plugin.logger.warning("前回削除しきれなかった $it をまだ削除できません。サーバー停止時に再試行します") }
            save(PendingJarDeletions(self + remaining)).onLeft { plugin.logger.warning(it) }
        }
    }

    /**
     * 予約ファイルを読み込む（存在しない・壊れている場合は空として扱う）
     */
    private fun load(): PendingJarDeletions {
        val file = pendingFile
        if (!file.exists()) return PendingJarDeletions()
        return try {
            Utils.json.decodeFromString(PendingJarDeletions.serializer(), file.readText())
        } catch (e: Exception) {
            plugin.logger.warning("$PENDING_FILE_NAME を読み込めなかったため、削除予約を空として扱います: ${e.message}")
            PendingJarDeletions()
        }
    }

    /**
     * 予約ファイルを保存する（予約が空になったらファイルごと削除する）
     */
    private fun save(pending: PendingJarDeletions): Either<String, Unit> {
        val file = pendingFile
        if (pending.files.isEmpty()) {
            // 空の予約ファイルを残さない
            if (file.exists() && !file.delete()) return "$PENDING_FILE_NAME を削除できませんでした".left()
            return Unit.right()
        }
        return AtomicFileWriter.write(file, Utils.json.encodeToString(PendingJarDeletions.serializer(), pending))
    }

    internal companion object {
        /** 予約ファイル名 */
        const val PENDING_FILE_NAME = "pending-delete.json"

        /**
         * 予約済みファイル名を順に削除する純粋なロジック
         *
         * DIに依存しないためテストから直接呼び出せる。
         * 既に存在しないファイルは「削除済み」として予約から外す。
         *
         * @param pending 予約済みのファイル名
         * @param pluginsDir 解決先の plugins ディレクトリ
         * @return 削除できたファイルと、まだ削除できず予約に残すファイル名
         */
        internal fun deletePending(
            pending: List<String>,
            pluginsDir: File
        ): Pair<List<File>, List<String>> {
            val deleted = mutableListOf<File>()
            val remaining = mutableListOf<String>()
            for (name in pending) {
                val file = File(pluginsDir, name)
                when {
                    !file.exists() -> Unit
                    file.delete() -> deleted.add(file)
                    else -> remaining.add(name)
                }
            }
            return deleted to remaining
        }
    }
}