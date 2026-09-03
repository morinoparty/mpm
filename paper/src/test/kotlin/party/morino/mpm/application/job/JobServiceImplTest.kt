/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.job

import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.application.model.UpdateResult
import party.morino.mpm.api.application.model.job.JobId
import party.morino.mpm.api.application.model.job.JobResult
import party.morino.mpm.api.application.model.job.JobSnapshot
import party.morino.mpm.api.application.model.job.JobState
import party.morino.mpm.api.application.model.job.JobType
import party.morino.mpm.api.shared.error.MpmError
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * 非同期ジョブの受付・進捗記録・終了状態の遷移を検証するテスト
 *
 * ジョブは実際のディスパッチャ上で走るため、仮想時間ではなく実時間で完了を待ち合わせる。
 */
@DisplayName("JobService - background job execution")
class JobServiceImplTest {
    companion object {
        // ジョブ完了待ちのタイムアウト（ミリ秒）
        private const val WAIT_TIMEOUT_MILLIS = 5_000L

        // ジョブ完了待ちのポーリング間隔（ミリ秒）
        private const val POLL_INTERVAL_MILLIS = 5L

        // 同時受付テストで起動するスレッド数
        private const val CONCURRENT_SUBMITTERS = 8

        // JobRecordが保持する進捗ログの上限（実装値と揃える）
        private const val MAX_PROGRESS_ENTRIES = 200

        // JobServiceImplが保持する終了済みジョブの上限（実装値と揃える）
        private const val MAX_RETAINED_FINISHED_JOBS = 50
    }

    private val jobService = JobServiceImpl()

    @AfterEach
    fun tearDown() {
        // テスト間でジョブとCoroutineScopeを持ち越さない
        jobService.shutdown()
    }

    @Test
    @DisplayName("Successful job records progress and result")
    fun successfulJobRecordsProgressAndResult() {
        val results = listOf(UpdateResult("SamplePlugin", "1.0.0", "1.1.0", success = true))

        val jobId =
            jobService
                .submit(JobType.UPDATE_ALL) { reportProgress ->
                    reportProgress("<green>SamplePlugin を更新中...")
                    JobResult.UpdateAll(results).right()
                }.getOrNull()
                ?.id
        assertNotNull(jobId)

        val snapshot = awaitFinished(jobId!!)
        assertEquals(JobState.SUCCEEDED, snapshot.state)
        assertEquals(results, (snapshot.result as JobResult.UpdateAll).results)
        assertNull(snapshot.errorMessage)
        assertNotNull(snapshot.finishedAt)

        // MiniMessageのタグを除いた平文が併記されていること
        assertEquals(1, snapshot.progress.size)
        assertEquals("SamplePlugin を更新中...", snapshot.progress.first().text)
        assertEquals("<green>SamplePlugin を更新中...", snapshot.progress.first().raw)
    }

    @Test
    @DisplayName("Job returning an error finishes as FAILED")
    fun jobReturningErrorFinishesAsFailed() {
        val jobId =
            jobService
                .submit(JobType.UPDATE_ALL) { MpmError.PluginError.UpdateInProgress.left() }
                .getOrNull()
                ?.id

        val snapshot = awaitFinished(jobId!!)
        assertEquals(JobState.FAILED, snapshot.state)
        assertNull(snapshot.result)
        // クライアントへはエラーの人間向けメッセージを渡す（data classのtoString()ではない）
        assertEquals(MpmError.PluginError.UpdateInProgress.message, snapshot.errorMessage)
    }

    @Test
    @DisplayName("Job throwing an exception finishes as FAILED")
    fun jobThrowingExceptionFinishesAsFailed() {
        val jobId =
            jobService
                .submit(JobType.UPDATE_ALL) { error("boom") }
                .getOrNull()
                ?.id

        val snapshot = awaitFinished(jobId!!)
        // 例外を握りつぶすとRUNNINGのまま完了しないジョブに見えてしまう
        assertEquals(JobState.FAILED, snapshot.state)
        assertEquals("boom", snapshot.errorMessage)
    }

    @Test
    @DisplayName("Second job of the same type is rejected while one is running")
    fun secondJobOfSameTypeIsRejected() {
        // 先行ジョブを実行中のまま留めるためのゲート
        val gate = CompletableDeferred<Unit>()
        val first =
            jobService.submit(JobType.UPDATE_ALL) {
                gate.await()
                JobResult.UpdateAll(emptyList()).right()
            }
        assertTrue(first.isRight())

        val second = jobService.submit(JobType.UPDATE_ALL) { JobResult.UpdateAll(emptyList()).right() }
        assertEquals(MpmError.PluginError.UpdateInProgress, second.leftOrNull())

        // 先行ジョブを完了させれば、次のジョブは受け付けられる
        gate.complete(Unit)
        awaitFinished(first.getOrNull()!!.id)
        assertTrue(jobService.submit(JobType.UPDATE_ALL) { JobResult.UpdateAll(emptyList()).right() }.isRight())
    }

    @Test
    @DisplayName("Unknown job id returns null")
    fun unknownJobIdReturnsNull() {
        assertNull(jobService.get(JobId("does-not-exist")))
    }

    @Test
    @DisplayName("Only one of many concurrent submits is accepted")
    fun onlyOneConcurrentSubmitIsAccepted() {
        // 先行ジョブを実行中のまま留めるためのゲート
        val gate = CompletableDeferred<Unit>()
        val accepted = AtomicInteger(0)
        val barrier = CountDownLatch(1)

        // 同時にsubmitして、受け付けられるのが厳密に1件であることを確かめる
        val threads =
            (1..CONCURRENT_SUBMITTERS).map {
                thread {
                    barrier.await()
                    val submitted =
                        jobService.submit(JobType.UPDATE_ALL) {
                            gate.await()
                            JobResult.UpdateAll(emptyList()).right()
                        }
                    if (submitted.isRight()) accepted.incrementAndGet()
                }
            }
        barrier.countDown()
        threads.forEach { it.join() }

        assertEquals(1, accepted.get())
        gate.complete(Unit)
    }

    @Test
    @DisplayName("Submit after shutdown is rejected")
    fun submitAfterShutdownIsRejected() {
        jobService.shutdown()

        val submitted = jobService.submit(JobType.UPDATE_ALL) { JobResult.UpdateAll(emptyList()).right() }
        // 停止後に受け付けると、キャンセル済みスコープに載ったジョブが永久にRUNNINGで残る
        assertTrue(submitted.isLeft())
        assertTrue(jobService.list().isEmpty())
    }

    @Test
    @DisplayName("Progress log keeps only the newest entries")
    fun progressLogKeepsOnlyNewestEntries() {
        val emitted = MAX_PROGRESS_ENTRIES + 50

        val jobId =
            jobService
                .submit(JobType.UPDATE_ALL) { reportProgress ->
                    repeat(emitted) { index -> reportProgress("line $index") }
                    JobResult.UpdateAll(emptyList()).right()
                }.getOrNull()
                ?.id

        val snapshot = awaitFinished(jobId!!)
        assertEquals(MAX_PROGRESS_ENTRIES, snapshot.progress.size)
        // 古い行から捨てられるため、先頭は「捨てられた分」だけ後ろにずれる
        assertEquals("line ${emitted - MAX_PROGRESS_ENTRIES}", snapshot.progress.first().text)
        assertEquals("line ${emitted - 1}", snapshot.progress.last().text)
    }

    @Test
    @DisplayName("Finished jobs are pruned down to the retention limit")
    fun finishedJobsArePrunedToRetentionLimit() {
        // 上限を超える数のジョブを順に完走させる（同種ジョブは同時実行できないため直列）
        repeat(MAX_RETAINED_FINISHED_JOBS + 5) {
            val jobId =
                jobService
                    .submit(JobType.UPDATE_ALL) { JobResult.UpdateAll(emptyList()).right() }
                    .getOrNull()
                    ?.id
            awaitFinished(jobId!!)
        }

        assertEquals(MAX_RETAINED_FINISHED_JOBS, jobService.list().size)
    }

    /**
     * ジョブが終了状態になるまで待つ
     *
     * @param id ジョブID
     * @return 終了時点のスナップショット
     */
    private fun awaitFinished(id: JobId): JobSnapshot =
        runBlocking {
            withTimeout(WAIT_TIMEOUT_MILLIS) {
                var snapshot = jobService.get(id)
                while (snapshot == null || snapshot.state == JobState.RUNNING) {
                    delay(POLL_INTERVAL_MILLIS)
                    snapshot = jobService.get(id)
                }
                snapshot
            }
        }
}