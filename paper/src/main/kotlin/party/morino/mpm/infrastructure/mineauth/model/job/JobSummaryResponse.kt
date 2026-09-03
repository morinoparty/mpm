/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth.model.job

import kotlinx.serialization.Serializable
import party.morino.mpm.api.application.model.job.JobSnapshot
import java.time.format.DateTimeFormatter

/**
 * ジョブ一覧（GET /jobs）の各エントリ
 *
 * 進捗ログと結果は件数によっては大きくなるため一覧には含めない。
 * それらが必要な場合は `GET /jobs/{id}` を参照する。
 *
 * @property id ジョブID
 * @property type ジョブ種別
 * @property state 実行状態（RUNNING / SUCCEEDED / FAILED）
 * @property createdAt 受付時刻（ISO-8601, UTC）
 * @property finishedAt 終了時刻（ISO-8601, UTC）。実行中はnull
 */
@Serializable
data class JobSummaryResponse(
    val id: String,
    val type: String,
    val state: String,
    val createdAt: String,
    val finishedAt: String?
) {
    companion object {
        /**
         * スナップショットからレスポンスDTOを生成する
         *
         * @param snapshot ジョブのスナップショット
         * @return レスポンスDTO
         */
        fun from(snapshot: JobSnapshot): JobSummaryResponse =
            JobSummaryResponse(
                id = snapshot.id.value,
                type = snapshot.type.name,
                state = snapshot.state.name,
                createdAt = DateTimeFormatter.ISO_INSTANT.format(snapshot.createdAt),
                finishedAt = snapshot.finishedAt?.let { DateTimeFormatter.ISO_INSTANT.format(it) }
            )
    }
}