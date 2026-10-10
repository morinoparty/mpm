/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.plugin.retire

import org.bukkit.Server
import java.io.File

/**
 * JVM の終了時に、削除予約されたJARを削除するシャットダウンフック
 *
 * mpm より後に停止するプラグインのJARは mpm の onDisable では削除できないため、このフックが後始末する。
 * SIGTERM で停止した場合は Paper の停止処理もシャットダウンフックとして並行して走るため、
 * 予約済みのJARを読み込んでいるプラグインがすべて無効化されるまで待ってから削除する。
 *
 * 実行時には mpm のクラスローダーが閉じられ、mpm 自身のJARも削除済みの場合があるため、
 * まだ読み込まれていないクラスを使わないよう、JDK と Bukkit API だけで処理する。
 * pending-delete.json は更新しないが、削除済みのファイルは次回起動時の片付けで予約から外れる。
 *
 * @param server プラグインの状態を確認するサーバー
 * @param pluginsDir 予約済みのファイル名を解決する plugins ディレクトリ
 * @param pendingNames 予約済みのファイル名（予約の変更に追従するため、並行して読み書きできる集合を共有する）
 */
class PendingJarShutdownHook(
    private val server: Server,
    private val pluginsDir: File,
    private val pendingNames: MutableSet<String>
) : Thread("mpm-pending-jar-deletion") {
    override fun run() {
        // 予約は削除の直前まで取り消されうる（ロールバックで同名のJARを置き直した場合など）ため、実行時点の内容を使う
        val targets = ArrayList<File>()
        for (name in pendingNames) targets.add(File(pluginsDir, name))
        if (targets.isEmpty()) return

        // 予約済みのJARを使うプラグインが停止しきるまで待つ（停止処理が固まっても終了を妨げないよう上限を設ける）
        val deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MILLIS
        while (hasEnabledOwner(targets) && System.currentTimeMillis() < deadline) {
            sleep(POLL_INTERVAL_MILLIS)
        }
        // 待ちきれなかった場合は消さずに残し、次回起動時の片付けに任せる
        if (hasEnabledOwner(targets)) return

        // 待っている間に取り消された予約は消さない
        // 削除できなかったファイルは pending-delete.json に残っているため、次回起動時に再試行される
        for (target in targets) {
            if (target.name in pendingNames) target.delete()
        }
    }

    /**
     * 削除対象のJARを読み込んでいる、有効なプラグインがあるかを判定する
     *
     * @param targets 削除対象のJAR
     * @return 有効なプラグインが読み込んでいれば true
     */
    private fun hasEnabledOwner(targets: List<File>): Boolean =
        server.pluginManager.plugins.any { plugin ->
            if (!plugin.isEnabled) return@any false
            // runningJarOf / isSameFile と同じ判定を、未読み込みのクラスを使わずに行う
            val jar =
                try {
                    plugin.javaClass.protectionDomain
                        ?.codeSource
                        ?.location
                        ?.toURI()
                        ?.let { File(it).canonicalFile }
                } catch (_: Exception) {
                    null
                } ?: return@any false
            targets.any { target -> target.canonicalFile == jar }
        }

    private companion object {
        /** プラグインの停止を待つ上限（ミリ秒） */
        const val WAIT_TIMEOUT_MILLIS = 60_000L

        /** プラグインの状態を確認する間隔（ミリ秒） */
        const val POLL_INTERVAL_MILLIS = 100L
    }
}
