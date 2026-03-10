/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.config.model

import kotlinx.serialization.Serializable

/**
 * スケジュール自動更新の設定
 *
 * cron形式でプラグインの自動更新スケジュールを指定する
 */
@Serializable
data class ScheduleConfig(
    // スケジュール自動更新の有効/無効
    val enabled: Boolean = false,

    // cron式（UNIX形式: 分 時 日 月 曜日）
    val cron: String = "0 4 * * *",

    // サーバー起動時にプラグインの更新チェックを行うか
    val checkOnStartup: Boolean = true,

    // dry-runモード（trueの場合、更新チェックと通知のみで実際の更新は行わない）
    val dryRun: Boolean = false,
)
