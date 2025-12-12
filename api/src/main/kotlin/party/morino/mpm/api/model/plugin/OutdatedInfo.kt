/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
and related and neighboring rights to this software to the public domain worldwide.
This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.model.plugin

import kotlinx.serialization.Serializable

/**
 * プラグインの更新情報
 */
@Serializable
data class OutdatedInfo(
    // プラグイン名
    val pluginName: String,
    // 現在のバージョン
    val currentVersion: String,
    // 最新のバージョン
    val latestVersion: String,
    // 更新が必要かどうか
    val needsUpdate: Boolean
)