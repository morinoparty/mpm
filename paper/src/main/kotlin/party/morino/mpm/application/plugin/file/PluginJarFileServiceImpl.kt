/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin.file

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.right
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.application.model.file.JarDeletionResult
import party.morino.mpm.api.application.model.file.JarFileInfo
import party.morino.mpm.api.application.plugin.DeferredJarDeletion
import party.morino.mpm.api.application.plugin.PluginJarFileService
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.model.plugin.PluginData
import party.morino.mpm.api.shared.error.MpmError
import party.morino.mpm.event.lifecycle.PluginJarDeleteEvent
import party.morino.mpm.utils.BukkitDispatcher
import party.morino.mpm.utils.PluginDataUtils
import party.morino.mpm.utils.SafeFileName
import party.morino.mpm.utils.isSameFile
import party.morino.mpm.utils.runningJarOf
import java.io.File
import java.time.Instant

/**
 * [PluginJarFileService] の実装
 *
 * 削除対象を plugins/ 直下の .jar に閉じ込める検証は [resolveJarFile] に純粋関数として切り出し、
 * DI に依存せずテストできるようにしている。
 */
class PluginJarFileServiceImpl :
    PluginJarFileService,
    KoinComponent {
    // Koinによる依存性注入
    private val plugin: JavaPlugin by inject()
    private val pluginDirectory: PluginDirectory by inject()
    private val deferredJarDeletion: DeferredJarDeletion by inject()

    override fun listJars(): List<JarFileInfo> {
        val runningJar = runningJarOf(plugin)
        return scanJarFiles(pluginDirectory.getPluginsDirectory()).map { file ->
            describeJar(
                file = file,
                running = runningJar != null && isSameFile(runningJar, file),
                pendingDeletion = deferredJarDeletion.isScheduled(file)
            )
        }
    }

    override suspend fun deleteJar(fileName: String): Either<MpmError, JarDeletionResult> =
        either {
            val target = resolveJarFile(pluginDirectory.getPluginsDirectory(), fileName).bind()
            val deferred = deleteOrSchedule(target).bind()

            // 削除結果が確定してから通知用イベントを発火する（Webhook 通知はリスナー側で行う）
            // PaperMCではイベントはメインスレッドで発火する必要があるため、BukkitDispatcherを使用
            BukkitDispatcher.callEventSync(
                plugin,
                PluginJarDeleteEvent(fileName = target.name, jarFile = target, deferred = deferred)
            )
            JarDeletionResult(fileName = target.name, deferred = deferred)
        }

    /**
     * JARを即時削除するか、できなければサーバー停止時の削除を予約する
     *
     * @param target 削除対象（plugins/ 直下の既存 .jar）
     * @return 予約に回した場合は true、即時削除できた場合は false
     */
    private fun deleteOrSchedule(target: File): Either<MpmError, Boolean> {
        // 実行中の mpm 自身のJARを消すとクラスローダー経由の読み込みが壊れるため、停止時の削除に回す
        val runningJar = runningJarOf(plugin)
        if (runningJar != null && isSameFile(runningJar, target)) {
            plugin.logger.info("${target.name} は実行中の mpm 自身のJARのため、サーバー停止時に削除します")
            return scheduleDeferred(target).map { true }
        }

        if (target.delete()) {
            plugin.logger.info("${target.name} を削除しました")
            return false.right()
        }

        // 実行中のJARがロックされる環境（Windows など）では即時削除できないため予約に回す
        plugin.logger.warning("${target.name} を削除できなかったため、サーバー停止時に削除します")
        return scheduleDeferred(target).map { true }
    }

    /**
     * サーバー停止時の削除を予約する（予約の永続化に失敗したら [MpmError.FileError.DeleteFailed]）
     */
    private fun scheduleDeferred(target: File): Either<MpmError, Unit> =
        deferredJarDeletion.schedule(target).fold(
            ifLeft = { reason -> MpmError.FileError.DeleteFailed(target.name, reason).left() },
            ifRight = { Unit.right() }
        )

    internal companion object {
        /** 削除を許可する拡張子 */
        private const val JAR_EXTENSION = ".jar"

        /**
         * plugins/ 直下の .jar をファイル名順に列挙する純粋なロジック
         *
         * listFiles は非再帰なので、サブディレクトリ配下は自動的に対象外になる。
         *
         * @param pluginsDir plugins ディレクトリ
         * @return 見つかった .jar（ディレクトリが無ければ空）
         */
        internal fun scanJarFiles(pluginsDir: File): List<File> =
            pluginsDir
                .listFiles { file -> file.isFile && file.name.endsWith(JAR_EXTENSION, ignoreCase = true) }
                .orEmpty()
                .sortedBy { it.name }

        /**
         * 1つのJARの情報を組み立てる
         *
         * plugin.yml / paper-plugin.yml が読めない・存在しないJARでも一覧から落とさず、
         * プラグイン名とバージョンを null にして返す（削除対象としては同じ扱いのため）。
         *
         * @param file 対象のJAR
         * @param running 実行中の mpm 自身のJARかどうか
         * @param pendingDeletion 停止時の削除が予約済みかどうか
         */
        internal fun describeJar(
            file: File,
            running: Boolean,
            pendingDeletion: Boolean
        ): JarFileInfo {
            // 壊れたJARなどで例外が発生しても一覧全体を止めない
            val pluginData = runCatching { PluginDataUtils.getPluginData(file) }.getOrNull()
            val (name, version) =
                when (pluginData) {
                    is PluginData.BukkitPluginData -> pluginData.name to pluginData.version
                    is PluginData.PaperPluginData -> pluginData.name to pluginData.version
                    null -> null to null
                }
            return JarFileInfo(
                fileName = file.name,
                sizeBytes = file.length(),
                lastModified = Instant.ofEpochMilli(file.lastModified()),
                // 空文字の name / version は「読めなかった」と同じ扱いにする
                pluginName = name?.ifBlank { null },
                pluginVersion = version?.ifBlank { null },
                running = running,
                pendingDeletion = pendingDeletion
            )
        }

        /**
         * ファイル名を plugins/ 直下の既存 .jar として解決する純粋なロジック
         *
         * パストラバーサル・サブディレクトリ・.jar 以外の拡張子・ディレクトリを弾く。
         * DI に依存しないためテストから直接呼び出せる。
         *
         * @param pluginsDir plugins ディレクトリ
         * @param fileName 削除対象のファイル名
         * @return 解決された既存の .jar ファイル。不正なら [MpmError.FileError.InvalidFileName]、
         *         存在しなければ [MpmError.FileError.NotFound]
         */
        internal fun resolveJarFile(
            pluginsDir: File,
            fileName: String
        ): Either<MpmError, File> =
            either {
                // パス区切り・".." などを弾き、正規化後も plugins/ 配下に収まることを確認する
                val file =
                    SafeFileName.resolveInside(pluginsDir, fileName).getOrElse { reason ->
                        raise(MpmError.FileError.InvalidFileName(fileName, reason))
                    }
                // 削除できるのは .jar だけ（config やディレクトリを誤って消せないようにする）
                ensure(fileName.endsWith(JAR_EXTENSION, ignoreCase = true)) {
                    MpmError.FileError.InvalidFileName(fileName, "only $JAR_EXTENSION files can be deleted")
                }
                ensure(file.exists()) { MpmError.FileError.NotFound(fileName) }
                // "x.jar" という名前のディレクトリは対象外
                ensure(file.isFile) {
                    MpmError.FileError.InvalidFileName(fileName, "not a regular file")
                }
                file
            }
    }
}