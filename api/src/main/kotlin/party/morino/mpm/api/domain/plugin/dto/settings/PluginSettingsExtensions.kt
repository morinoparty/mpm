/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.plugin.dto.settings

import party.morino.mpm.api.domain.config.model.GlobalSettings

/**
 * PluginSettingsの値をGlobalSettingsでフォールバックして解決済みの値を取得する
 *
 * **現在mpm本体はこの関数を呼び出していない。**
 * ロックの判定は `metadata/<プラグイン名>.yaml` の `settings.lock == true` だけで行っており、
 * GlobalSettings へのフォールバックは存在しない（nullはfalseと同じ扱い）。
 * この関数を配線するとcronの更新対象判定が変わるため、利用者の判断なしに有効化しないこと。
 *
 * @param globalSettings グローバル設定
 * @return 解決済みのプラグイン設定
 */
fun PluginSettings.resolve(globalSettings: GlobalSettings): ResolvedPluginSettings =
    ResolvedPluginSettings(
        // nullの場合はGlobalSettingsの値を使用
        lock = this.lock ?: globalSettings.lock,
        autoUpdate = this.autoUpdate ?: globalSettings.autoUpdate,
        autoCheck = this.autoCheck ?: globalSettings.autoCheck
    )

/**
 * 解決済みのプラグイン設定
 *
 * PluginSettingsとGlobalSettingsの優先順位を解決した結果を保持する
 * すべてのフィールドはnon-nullのBoolean値を持つ
 */
data class ResolvedPluginSettings(
    // バージョンをロックするか（trueの場合、updateコマンドでも更新されない）
    val lock: Boolean,
    // 自動更新を有効にするか
    val autoUpdate: Boolean,
    // 自動バージョンチェックを有効にするか
    val autoCheck: Boolean
)