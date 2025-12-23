/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.event

import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import party.morino.mpm.api.model.plugin.InstalledPlugin

/**
 * 更新可能なプラグインが検出された際に発火するイベント
 *
 * このイベントはプラグインの更新チェック時に、新しいバージョンが利用可能な場合に呼び出される。
 * 他のプラグインはこのイベントをリッスンして、更新通知を表示したり、
 * 自動更新処理をトリガーしたりすることができる。
 *
 * @property installedPlugin 更新可能なインストール済みプラグイン
 * @property currentVersion 現在インストールされているバージョン
 * @property latestVersion 利用可能な最新バージョン
 */
class PluginOutdatedEvent(
    val installedPlugin: InstalledPlugin,
    val currentVersion: String,
    val latestVersion: String
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