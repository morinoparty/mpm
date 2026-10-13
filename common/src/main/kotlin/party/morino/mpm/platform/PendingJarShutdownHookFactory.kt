/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.platform

import java.io.File

/**
 * JVM の終了時に削除予約済みのJARを削除するシャットダウンフックを作る
 *
 * プラグインの停止順や状態の確認方法はプラットフォームごとに異なるため、フックの中身は各プラットフォームが実装する。
 */
fun interface PendingJarShutdownHookFactory {
    /**
     * シャットダウンフック用のスレッドを作る
     *
     * @param pluginsDir 予約済みのファイル名を解決する plugins ディレクトリ
     * @param pendingNames 予約済みのファイル名（予約の変更に追従するため、並行して読み書きできる集合を共有する）
     * @return Runtime.addShutdownHook に渡すスレッド
     */
    fun create(
        pluginsDir: File,
        pendingNames: MutableSet<String>
    ): Thread
}