/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.event

import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import party.morino.mpm.api.model.plugin.InstalledPlugin

/**
 * プラグインがロックされる際に発火するイベント
 *
 * このイベントはプラグインのロック処理（自動更新の禁止）の前に呼び出される。
 * Cancellableを実装しているため、他のプラグインがこのイベントをキャンセルすることで
 * ロック処理を中止させることができる。
 *
 * @property installedPlugin ロック対象のインストール済みプラグイン
 * @property currentVersion 現在のバージョン
 */
class PluginLockEvent(
    val installedPlugin: InstalledPlugin,
    val currentVersion: String
) : Event(),
    Cancellable {
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

    private var isCancelled: Boolean = false

    override fun isCancelled(): Boolean = isCancelled

    override fun setCancelled(cancel: Boolean) {
        this.isCancelled = cancel
    }

    override fun getHandlers(): HandlerList = HANDLER_LIST
}