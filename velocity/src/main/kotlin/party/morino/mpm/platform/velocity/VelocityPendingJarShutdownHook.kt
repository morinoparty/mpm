/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.platform.velocity

import java.io.File

/**
 * JVM の終了時に、削除予約されたJARを削除するシャットダウンフック（Velocity 用）
 *
 * 通常は ProxyShutdownEvent で削除済みのため、このフックは削除に失敗したもの（Windows など）の再試行と、
 * ProxyShutdownEvent が届かなかった場合の後始末を担う。
 *
 * プロキシの停止後はプラグインのスレッドプールが閉じられ、ログ出力も止まっているため、
 * イベントの発火やログ出力は行わず、JDK のクラスだけで処理する。
 * pending-delete.json は更新しないが、削除済みのファイルは次回起動時の片付けで予約から外れる。
 *
 * @param pluginsDir 予約済みのファイル名を解決する plugins ディレクトリ
 * @param pendingNames 予約済みのファイル名（予約の変更に追従するため、並行して読み書きできる集合を共有する）
 */
class VelocityPendingJarShutdownHook(
    private val pluginsDir: File,
    private val pendingNames: MutableSet<String>
) : Thread("mpm-pending-jar-deletion") {
    override fun run() {
        // 予約は削除の直前まで取り消されうるため、実行時点の内容を使う（並行集合のため反復中の変更にも耐える）
        // JARの削除後でも動くよう、Kotlin 標準ライブラリの関数を使わず JDK の反復だけで処理する
        // 削除できなかったファイルは pending-delete.json に残っているため、次回起動時に再試行される
        for (name in pendingNames) {
            File(pluginsDir, name).delete()
        }
    }
}