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

package party.morino.mpm.api.application.model.job

import java.time.Instant

/**
 * ある時点でのジョブの状態を写し取った不変のスナップショット
 *
 * ジョブ本体はバックグラウンドのコルーチンから更新され続けるため、
 * 参照側（HTTPハンドラー）へは常にこのコピーを返して整合性を保つ。
 *
 * @property id ジョブID
 * @property type ジョブ種別
 * @property state 実行状態
 * @property createdAt 受付時刻
 * @property finishedAt 終了時刻。実行中はnull
 * @property progress 進捗ログ（古い順。上限を超えた古い行は捨てられる）
 * @property result 成功時の結果。実行中・失敗時はnull
 * @property errorMessage 失敗時の理由。実行中・成功時はnull
 */
data class JobSnapshot(
    val id: JobId,
    val type: JobType,
    val state: JobState,
    val createdAt: Instant,
    val finishedAt: Instant?,
    val progress: List<JobProgressEntry>,
    val result: JobResult?,
    val errorMessage: String?
)