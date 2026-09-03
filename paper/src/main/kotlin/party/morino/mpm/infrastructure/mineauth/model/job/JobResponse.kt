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
import party.morino.mpm.api.application.model.UpdateResult
import party.morino.mpm.api.application.model.job.JobResult
import party.morino.mpm.api.application.model.job.JobSnapshot
import java.time.format.DateTimeFormatter

/**
 * ジョブ詳細（GET /jobs/{id}、POST /jobs）のレスポンス
 *
 * @property id ジョブID
 * @property type ジョブ種別
 * @property state 実行状態（RUNNING / SUCCEEDED / FAILED）
 * @property createdAt 受付時刻（ISO-8601, UTC）
 * @property finishedAt 終了時刻（ISO-8601, UTC）。実行中はnull
 * @property progress 進捗ログ（古い順）
 * @property updateResults `UPDATE_ALL` が成功した場合の更新結果。それ以外はnull
 * @property errorMessage 失敗理由。実行中・成功時はnull
 */
@Serializable
data class JobResponse(
    val id: String,
    val type: String,
    val state: String,
    val createdAt: String,
    val finishedAt: String?,
    val progress: List<JobProgressEntryResponse>,
    val updateResults: List<UpdateResult>?,
    val errorMessage: String?
) {
    companion object {
        /**
         * スナップショットからレスポンスDTOを生成する
         *
         * 結果はジョブ種別ごとに型が異なるため、種別に対応するフィールドへ振り分ける。
         *
         * @param snapshot ジョブのスナップショット
         * @return レスポンスDTO
         */
        fun from(snapshot: JobSnapshot): JobResponse =
            JobResponse(
                id = snapshot.id.value,
                type = snapshot.type.name,
                state = snapshot.state.name,
                createdAt = DateTimeFormatter.ISO_INSTANT.format(snapshot.createdAt),
                finishedAt = snapshot.finishedAt?.let { DateTimeFormatter.ISO_INSTANT.format(it) },
                progress = snapshot.progress.map { JobProgressEntryResponse.from(it) },
                updateResults = (snapshot.result as? JobResult.UpdateAll)?.results,
                errorMessage = snapshot.errorMessage
            )
    }
}