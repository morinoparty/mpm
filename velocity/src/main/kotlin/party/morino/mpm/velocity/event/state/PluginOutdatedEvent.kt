/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.velocity.event.state

import party.morino.mpm.api.model.plugin.InstalledPlugin

/**
 * 定期チェックで新しいバージョンを検出したときに発火するイベント
 *
 * 通知のためのイベントのため、結果を持たない（拒否できない）。
 *
 * @property installedPlugin 新しいバージョンが見つかったプラグイン
 * @property currentVersion 現在のバージョン
 * @property latestVersion 検出した最新バージョン
 */
class PluginOutdatedEvent(
    val installedPlugin: InstalledPlugin,
    val currentVersion: String,
    val latestVersion: String
)