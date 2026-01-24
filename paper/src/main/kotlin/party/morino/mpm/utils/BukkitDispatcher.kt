/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
 * and related and neighboring rights to this software to the public domain worldwide.
 * This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils

import kotlinx.coroutines.suspendCancellableCoroutine
import org.bukkit.Bukkit
import org.bukkit.event.Event
import org.bukkit.plugin.Plugin
import kotlin.coroutines.resume

/**
 * Bukkitのメインスレッドでイベントを発火するためのユーティリティ
 *
 * PaperMCではイベントはメインスレッド（同期的）でのみ発火可能なため、
 * コルーチン内から呼び出す場合はこのユーティリティを使用する
 */
object BukkitDispatcher {
    /**
     * メインスレッドでイベントを発火し、完了を待機する
     *
     * @param plugin プラグインインスタンス
     * @param event 発火するイベント
     * @return 発火されたイベント（キャンセル状態などを確認可能）
     */
    suspend fun <T : Event> callEventSync(plugin: Plugin, event: T): T {
        // 既にメインスレッドにいる場合はそのまま実行
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getPluginManager().callEvent(event)
            return event
        }

        // 非同期スレッドからの場合はメインスレッドに切り替えて実行
        return suspendCancellableCoroutine { continuation ->
            Bukkit.getScheduler().runTask(plugin, Runnable {
                Bukkit.getPluginManager().callEvent(event)
                continuation.resume(event)
            })
        }
    }
}
