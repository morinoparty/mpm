/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.application.model.outdated

import kotlinx.serialization.Serializable

/**
 * プラグインの更新情報
 *
 * アプリケーション層で使用される結果DTO
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
    val needsUpdate: Boolean,
    // このチェックで latest が前回メタデータに記録された値から変化したか
    //
    // 「新しい上流リリースを検知した瞬間」を表す。更新可能な状態が続いているだけの
    // プラグインは毎回のチェックで false になるため、通知の重複抑制に使える。
    // メタデータを読めなかった場合など、判定できないときは false とする。
    val latestChanged: Boolean = false
)