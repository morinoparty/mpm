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

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import party.morino.mpm.api.application.job.JobService
import party.morino.mpm.api.application.model.job.JobId
import party.morino.mpm.api.application.model.job.JobProgressEntry
import party.morino.mpm.api.application.model.job.JobResult
import party.morino.mpm.api.application.model.job.JobSnapshot
import party.morino.mpm.api.application.model.job.JobType
import party.morino.mpm.api.shared.error.MpmError
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * メモリ上でジョブを管理する [JobService] の実装
 *
 * 実処理はIOディスパッチャ上のコルーチンで走らせ、呼び出し元（HTTPハンドラー）は
 * ジョブIDを受け取ってすぐに応答を返せるようにする。
 * 個々のジョブの失敗が他へ波及しないよう、スコープには [SupervisorJob] を使う。
 */
class JobServiceImpl : JobService {
    companion object {
        // 保持する終了済みジョブの上限。超えた分は古いものから捨てる
        private const val MAX_RETAINED_FINISHED_JOBS = 50

        // shutdown時に実行中ジョブの終了を待つ上限（ミリ秒）。
        // ダウンロード中などキャンセルに即応できない区間があるため、
        // 無期限に待ってサーバーの停止を止めないよう区切りを設ける
        private const val SHUTDOWN_TIMEOUT_MILLIS = 5_000L
    }

    private val logger: Logger = Logger.getLogger(JobServiceImpl::class.java.name)

    // ジョブの保管庫（キーはジョブID文字列）
    private val jobs = ConcurrentHashMap<String, JobRecord>()

    // 「受付可否の判定」「ジョブの登録と起動」「終了済みジョブの間引き」「停止の宣言」を
    // 不可分に行うためのロック。2つのリクエストが同時に来ても片方だけが受け付けられ、
    // 停止処理と受付が交差しないことを保証する
    private val submitLock = Any()

    // バックグラウンド実行中のジョブをまとめて束ねる親ジョブ（shutdownで待ち合わせる）
    private val supervisor = SupervisorJob()

    // バックグラウンド実行用のCoroutineScope
    private val scope = CoroutineScope(Dispatchers.IO + supervisor)

    // 停止済みかどうか。書き込みは [submitLock] の下でのみ行う
    @Volatile
    private var isShutdown = false

    override fun submit(
        type: JobType,
        block: suspend (reportProgress: (String) -> Unit) -> Either<MpmError, JobResult>
    ): Either<MpmError, JobSnapshot> =
        synchronized(submitLock) {
            // 停止後に受け付けると、キャンセル済みスコープに載せたジョブが
            // 永久にRUNNINGのまま残ってしまう
            if (isShutdown) {
                return MpmError.Unknown("Job service is shutting down").left()
            }

            // 同種のジョブが走っている間は受け付けない。
            // 実処理側のMutexでも弾かれるが、そこまで進むとジョブが「失敗」として
            // 記録されてしまうため、受付段階で断って409を返せるようにする
            if (jobs.values.any { it.type == type && it.isRunning }) {
                return MpmError.PluginError.UpdateInProgress.left()
            }

            val record = JobRecord(JobId.generate(), type, Instant.now())
            jobs[record.id.value] = record
            // 起動もロックの内側で行い、shutdownとの交差を防ぐ
            scope.launch { runJob(record, block) }
            // 受付直後の状態をそのまま返す。呼び出し側にget()で読み直させると、
            // その間にshutdownが走った場合に「受け付けたのに見つからない」状態になる
            record.snapshot().right()
        }

    override fun get(id: JobId): JobSnapshot? = jobs[id.value]?.snapshot()

    override fun list(): List<JobSnapshot> =
        jobs.values
            .map { it.snapshot() }
            // 受付が新しい順に並べる（同時刻の場合はIDで安定させる）
            .sortedWith(compareByDescending<JobSnapshot> { it.createdAt }.thenBy { it.id.value })

    override fun shutdown() {
        synchronized(submitLock) {
            if (isShutdown) return
            // 以降の受付を止めてから停止処理に入る
            isShutdown = true
        }

        // 実行中のジョブにキャンセルを通知し、終了を待つ。
        // 待たずに戻ると、Koin停止後もBeanを掴んだままのコルーチンが走り続けることになる。
        //
        // ただし待機には上限を設けている。ダウンロード中などキャンセルに即応できない区間が
        // 残っていた場合に無期限に待つと、サーバーの停止そのものを止めてしまうためである。
        // 上限に達した場合は「Koin停止後も僅かな時間ジョブが走り得る」ことを受け入れ、
        // 検知できるよう警告ログを残す
        runBlocking {
            val finished = withTimeoutOrNull(SHUTDOWN_TIMEOUT_MILLIS) { supervisor.cancelAndJoin() }
            if (finished == null) {
                logger.warning(
                    "実行中ジョブの終了を${SHUTDOWN_TIMEOUT_MILLIS}ms待ちましたが完了しませんでした " +
                        "（残: ${jobs.values.count { it.isRunning }}件）"
                )
            }
        }

        jobs.clear()
    }

    /**
     * ジョブ本体を実行し、結果をレコードに書き戻す
     *
     * 例外はここで受け止めて「失敗したジョブ」として記録する。握りつぶすと
     * クライアントからはRUNNINGのまま永久に完了しないジョブに見えてしまう。
     *
     * @param record 対象のジョブ
     * @param block 実処理
     */
    private suspend fun runJob(
        record: JobRecord,
        block: suspend (reportProgress: (String) -> Unit) -> Either<MpmError, JobResult>
    ) {
        try {
            block { message -> record.appendProgress(toProgressEntry(message)) }
                .fold(
                    // 失敗時の処理
                    { error -> finish(record) { record.fail(error.message, Instant.now()) } },
                    // 成功時の処理
                    { result -> finish(record) { record.succeed(result, Instant.now()) } }
                )
        } catch (e: CancellationException) {
            // shutdown時のキャンセル。スコープの終了処理を妨げないよう再送出する
            finish(record) { record.fail("Job was cancelled", Instant.now()) }
            throw e
        } catch (e: Exception) {
            logger.warning("[job] ${record.type} (${record.id.value}) が例外で終了しました: ${e.message}")
            finish(record) { record.fail(e.message ?: e::class.simpleName ?: "Unknown error", Instant.now()) }
        }
    }

    /**
     * ジョブを終端状態へ遷移させる
     *
     * 遷移してから間引くと「上限＋1件」が一瞬観測できてしまうため、
     * 自分が占める1枠を先に空けてから遷移させ、常に上限を満たすようにする。
     * 間引きと遷移を同じロックの下で行うのはそのためである。
     *
     * @param record 対象のジョブ
     * @param transition 終端状態への遷移処理
     */
    private fun finish(
        record: JobRecord,
        transition: () -> Unit
    ) {
        synchronized(submitLock) {
            // このジョブが終了済みの枠を1つ使うため、上限から1を引いた数まで先に間引く
            pruneFinishedJobs(reservedSlots = 1)
            transition()
        }
    }

    /**
     * 終了済みジョブを上限まで間引く
     *
     * 呼び出し側で [submitLock] を保持していることを前提とする。
     *
     * @param reservedSlots これから終端状態になるジョブのために空けておく枠の数
     */
    private fun pruneFinishedJobs(reservedSlots: Int) {
        val finished = jobs.values.filterNot { it.isRunning }
        val excess = finished.size - (MAX_RETAINED_FINISHED_JOBS - reservedSlots)
        if (excess <= 0) return

        finished
            .sortedBy { it.createdAt }
            .take(excess)
            .forEach { jobs.remove(it.id.value) }
    }

    /**
     * サービス層の進捗メッセージを進捗ログの1行に変換する
     *
     * 進捗メッセージはゲーム内チャット向けのMiniMessage形式で流れてくるため、
     * Webクライアントがそのまま表示できる平文を併せて用意する。
     *
     * @param raw MiniMessage形式の進捗メッセージ
     * @return 進捗ログのエントリ
     */
    private fun toProgressEntry(raw: String): JobProgressEntry =
        JobProgressEntry(
            timestamp = Instant.now(),
            raw = raw,
            text = toPlainText(raw)
        )

    /**
     * MiniMessageのタグを取り除いた平文を得る
     *
     * 解釈に失敗した場合（想定外のタグなど）は原文をそのまま平文として扱う。
     * 進捗表示のために更新処理を落とすわけにはいかないため、ここは寛容に倒す。
     *
     * @param raw MiniMessage形式の文字列
     * @return タグを除去した平文
     */
    private fun toPlainText(raw: String): String =
        try {
            PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(raw))
        } catch (e: Exception) {
            logger.fine("[job] 進捗メッセージの平文化に失敗: ${e.message}")
            raw
        }
}