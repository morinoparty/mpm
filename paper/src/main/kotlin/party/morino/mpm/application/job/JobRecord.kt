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

package party.morino.mpm.application.job

import party.morino.mpm.api.application.model.job.JobId
import party.morino.mpm.api.application.model.job.JobProgressEntry
import party.morino.mpm.api.application.model.job.JobResult
import party.morino.mpm.api.application.model.job.JobSnapshot
import party.morino.mpm.api.application.model.job.JobState
import party.morino.mpm.api.application.model.job.JobType
import java.time.Instant

/**
 * 実行中・実行済みジョブの可変な内部状態
 *
 * 進捗の追記はバックグラウンドのコルーチンから、参照はHTTPのリクエストスレッドから
 * 行われるため、状態の読み書きはすべてこのクラスのモニターで直列化する。
 * 外部へは常に [snapshot] で不変のコピーを渡し、途中経過を共有しない。
 *
 * @property id ジョブID
 * @property type ジョブ種別
 * @property createdAt 受付時刻
 */
internal class JobRecord(
    val id: JobId,
    val type: JobType,
    val createdAt: Instant
) {
    companion object {
        // 進捗ログの保持上限。一括更新は1プラグインにつき複数行を出すため、
        // 際限なく貯めるとプラグイン数に比例してメモリを食う
        private const val MAX_PROGRESS_ENTRIES = 200
    }

    // 進捗ログ（古い順）。上限を超えたら古い行から捨てる
    private val progress = ArrayDeque<JobProgressEntry>()

    private var state: JobState = JobState.RUNNING
    private var finishedAt: Instant? = null
    private var result: JobResult? = null
    private var errorMessage: String? = null

    /** ジョブが実行中かどうか（同種ジョブの二重受付を防ぐ判定に使う） */
    val isRunning: Boolean
        @Synchronized get() = state == JobState.RUNNING

    /**
     * 進捗ログを1行追記する
     *
     * @param entry 追記するログ
     */
    @Synchronized
    fun appendProgress(entry: JobProgressEntry) {
        progress.addLast(entry)
        // 上限を超えた分は先頭（最も古い行）から捨てる
        while (progress.size > MAX_PROGRESS_ENTRIES) {
            progress.removeFirst()
        }
    }

    /**
     * ジョブを成功として確定する
     *
     * @param result 処理結果
     * @param finishedAt 終了時刻
     */
    @Synchronized
    fun succeed(
        result: JobResult,
        finishedAt: Instant
    ) {
        this.state = JobState.SUCCEEDED
        this.result = result
        this.finishedAt = finishedAt
    }

    /**
     * ジョブを失敗として確定する
     *
     * @param message 失敗理由
     * @param finishedAt 終了時刻
     */
    @Synchronized
    fun fail(
        message: String,
        finishedAt: Instant
    ) {
        this.state = JobState.FAILED
        this.errorMessage = message
        this.finishedAt = finishedAt
    }

    /**
     * 現在の状態を不変のスナップショットとして写し取る
     *
     * @return スナップショット
     */
    @Synchronized
    fun snapshot(): JobSnapshot =
        JobSnapshot(
            id = id,
            type = type,
            state = state,
            createdAt = createdAt,
            finishedAt = finishedAt,
            // 呼び出し側に内部のリストを渡さないようコピーする
            progress = progress.toList(),
            result = result,
            errorMessage = errorMessage
        )
}