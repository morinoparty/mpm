/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.application.model.UpdateResult
import party.morino.mpm.api.application.model.job.JobId
import party.morino.mpm.api.application.model.job.JobProgressEntry
import party.morino.mpm.api.application.model.job.JobResult
import party.morino.mpm.api.application.model.job.JobSnapshot
import party.morino.mpm.api.application.model.job.JobState
import party.morino.mpm.api.application.model.job.JobType
import party.morino.mpm.infrastructure.mineauth.model.job.JobResponse
import party.morino.mpm.infrastructure.mineauth.model.job.JobSummaryResponse
import java.time.Instant

/**
 * ジョブのスナップショットからHTTPレスポンスDTOへの変換を検証するテスト
 *
 * サービス層は時刻を [Instant] で保持し、文字列化はこの変換時にのみ行う。
 */
@DisplayName("Job response DTO conversion")
class JobResponseTest {
    private val createdAt: Instant = Instant.parse("2026-09-03T04:10:00Z")
    private val finishedAt: Instant = Instant.parse("2026-09-03T04:12:31Z")

    @Test
    @DisplayName("Succeeded snapshot carries results and ISO-8601 timestamps")
    fun succeededSnapshotCarriesResults() {
        val results = listOf(UpdateResult("SamplePlugin", "1.0.0", "1.1.0", success = true))
        val response = JobResponse.from(snapshot(JobState.SUCCEEDED, JobResult.UpdateAll(results)))

        assertEquals("UPDATE_ALL", response.type)
        assertEquals("SUCCEEDED", response.state)
        assertEquals("2026-09-03T04:10:00Z", response.createdAt)
        assertEquals("2026-09-03T04:12:31Z", response.finishedAt)
        assertEquals(results, response.updateResults)
        assertNull(response.errorMessage)

        // 進捗は原文と平文の両方をそのまま渡す
        assertEquals(1, response.progress.size)
        assertEquals("更新中", response.progress.first().text)
        assertEquals("<gray>更新中", response.progress.first().raw)
    }

    @Test
    @DisplayName("Running snapshot has no result and no finishedAt")
    fun runningSnapshotHasNoResult() {
        val response = JobResponse.from(snapshot(JobState.RUNNING, result = null, finishedAt = null))

        assertEquals("RUNNING", response.state)
        assertNull(response.finishedAt)
        assertNull(response.updateResults)
    }

    @Test
    @DisplayName("Summary omits progress and result")
    fun summaryOmitsProgressAndResult() {
        val summary = JobSummaryResponse.from(snapshot(JobState.SUCCEEDED, JobResult.UpdateAll(emptyList())))

        assertEquals("2026-09-03T04:10:00Z", summary.createdAt)
        assertEquals("2026-09-03T04:12:31Z", summary.finishedAt)
    }

    /**
     * テスト用のスナップショットを組み立てる
     *
     * @param state 実行状態
     * @param result 成功結果
     * @param finishedAt 終了時刻
     * @return スナップショット
     */
    private fun snapshot(
        state: JobState,
        result: JobResult?,
        finishedAt: Instant? = this.finishedAt
    ): JobSnapshot =
        JobSnapshot(
            id = JobId("0f9c2f4e-1a7b-4c1e-9a55-2c0f0a1b3d21"),
            type = JobType.UPDATE_ALL,
            state = state,
            createdAt = createdAt,
            finishedAt = finishedAt,
            progress = listOf(JobProgressEntry(createdAt, "<gray>更新中", "更新中")),
            result = result,
            errorMessage = null
        )
}