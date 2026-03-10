/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.application.scheduler

/**
 * プラグインの自動更新スケジューラー
 *
 * cron式に基づいてプラグインの更新チェック・自動更新を実行する
 */
interface UpdateScheduler {
    /**
     * スケジューラーを開始する
     *
     * config.jsonのschedule設定に基づいて、
     * 起動時チェックとcronスケジュールを開始する
     */
    fun start()

    /**
     * スケジューラーを停止する
     *
     * 実行中のスケジュールタスクをキャンセルする
     */
    fun stop()

    /**
     * スケジューラーを再起動する
     *
     * 設定変更後に呼び出すことで、新しい設定を反映する
     */
    fun restart()
}
