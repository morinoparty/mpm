/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.event.lifecycle

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import java.io.File

/**
 * plugins/ 直下のJARファイルが直接削除されたときに発火するイベント
 *
 * `mpm uninstall` と異なり mpm.json やメタデータには触れない、ファイル単体の削除を表す。
 * 実行中のJARは即時削除できないため、その場合は削除予約が完了した時点で [deferred] = true として発火する。
 * 削除結果（即時か予約か）が確定してから発火するため、[PluginUninstallEvent] と異なりキャンセルはできない。
 *
 * @property fileName 削除されたファイル名（plugins/ からの相対）
 * @property jarFile 削除されたJARファイル
 * @property deferred true の場合はまだディスク上に残っており、サーバー停止時に削除される
 */
class PluginJarDeleteEvent(
    val fileName: String,
    val jarFile: File,
    val deferred: Boolean
) : Event() {
    companion object {
        @JvmStatic
        private val HANDLER_LIST: HandlerList = HandlerList()

        /**
         * イベントのハンドラリストを取得します。
         * 必須メソッドです。
         * @return イベントのハンドラリスト
         */
        @JvmStatic
        fun getHandlerList(): HandlerList = HANDLER_LIST
    }

    override fun getHandlers(): HandlerList = HANDLER_LIST
}