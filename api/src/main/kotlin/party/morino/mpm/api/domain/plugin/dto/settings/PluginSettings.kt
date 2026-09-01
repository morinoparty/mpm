/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.plugin.dto.settings

import kotlinx.serialization.Serializable

/**
 * プラグイン固有の設定
 *
 * 各フィールドは省略可能（null）だが、GlobalSettings へのフォールバックは行われない。
 * 判定はいずれも `== true` で行うため、null は false と同じ扱いになる。
 */
@Serializable
data class PluginSettings(
    // バージョンをロックするか（trueの場合、updateコマンドでも更新されない）
    // nullの場合はfalseと同じ扱い（GlobalSettings.lockは参照されない）
    val lock: Boolean? = null,
    // 自動更新を有効にするか（現在は未使用の予約フィールド）
    // 自動更新の対象判定は ScheduleConfig と mpm.json のバージョン指定で行う
    val autoUpdate: Boolean? = null,
    // 自動バージョンチェックを有効にするか（現在は未使用の予約フィールド）
    val autoCheck: Boolean? = null
)