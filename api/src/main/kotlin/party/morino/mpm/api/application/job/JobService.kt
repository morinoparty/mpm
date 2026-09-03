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

package party.morino.mpm.api.application.job

import arrow.core.Either
import party.morino.mpm.api.application.model.job.JobId
import party.morino.mpm.api.application.model.job.JobResult
import party.morino.mpm.api.application.model.job.JobSnapshot
import party.morino.mpm.api.application.model.job.JobType
import party.morino.mpm.api.shared.error.MpmError

/**
 * 長時間かかる処理をバックグラウンドで実行し、進捗と結果を保持するサービス
 *
 * 全プラグインの一括更新のような処理は数分かかることがあり、HTTPのレスポンスを
 * 待たせ続けるとリバースプロキシやブラウザのタイムアウトに先に到達してしまう。
 * このサービスは処理を即座に受け付けてジョブIDを返し、呼び出し側が
 * ポーリングで進捗と完了を知れるようにする。
 *
 * ジョブはメモリ上にのみ保持されるため、サーバー再起動で失われる。
 * 更新処理そのものはメタデータとlockファイルに永続化されるため、
 * 失われるのは「進捗ログと結果の記録」だけである。
 */
interface JobService {
    /**
     * ジョブを受け付けてバックグラウンドで実行を開始する
     *
     * 同じ [type] のジョブがすでに実行中の場合は受け付けず
     * [MpmError.PluginError.UpdateInProgress] を返す（HTTPでは409になる）。
     *
     * 受け付けた直後のスナップショットをそのまま返すため、呼び出し側が改めて
     * [get] で読み直す必要はない（読み直しは [shutdown] と競合し得る）。
     *
     * @param type ジョブ種別
     * @param block 実際の処理。引数の関数を呼ぶと進捗ログに1行追記される
     *   （サービス層の進捗コールバックと同じMiniMessage形式の文字列を渡す）
     * @return 受け付けたジョブのスナップショット
     */
    fun submit(
        type: JobType,
        block: suspend (reportProgress: (String) -> Unit) -> Either<MpmError, JobResult>
    ): Either<MpmError, JobSnapshot>

    /**
     * ジョブの現在の状態を取得する
     *
     * @param id ジョブID
     * @return スナップショット。該当するジョブが無い場合はnull
     */
    fun get(id: JobId): JobSnapshot?

    /**
     * 保持しているジョブの一覧を取得する
     *
     * @return 受付が新しい順のスナップショット一覧
     */
    fun list(): List<JobSnapshot>

    /**
     * 実行中のジョブを打ち切り、内部のCoroutineScopeを破棄する
     *
     * プラグイン無効化時に呼び出す。呼び出し後は [submit] を受け付けない。
     * 実行中のジョブにキャンセルを通知したうえで、それらが終了するまで
     * （一定時間を上限として）待ち合わせる。
     */
    fun shutdown()
}