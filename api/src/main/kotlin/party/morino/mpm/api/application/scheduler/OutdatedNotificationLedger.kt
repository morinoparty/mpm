/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.application.scheduler

import arrow.core.Either

/**
 * `outdated` 通知の「通知済み」台帳
 *
 * 定期チェック（起動時チェック / cron）が Discord Webhook などへ送った
 * 「プラグイン名 -> 通知したときの上流の最新バージョン（raw）」を保持する。
 * 更新可能な状態が続いているだけのプラグインを毎回鳴らさず、
 * 上流に新しい版が出たときだけもう一度通知するための判定に使う。
 *
 * メタデータの `version.latest` とは別に持つのは、あちらが `mpm outdated` / `mpm doctor` /
 * HTTP API など人手の操作でも書き戻されるためである。「前回の記録から latest が変わったか」を
 * 判定に使うと、人が先にチェックした時点で変化が消費され、定期チェックからは
 * 一度も通知されなくなる。この台帳を書くのは定期チェックだけである。
 */
interface OutdatedNotificationLedger {
    /**
     * 通知済みの「プラグイン名 -> 通知時の最新バージョン（raw）」を読み込む
     *
     * 台帳がまだ無い、あるいは読めない場合は空のマップを返す（その回は全件が通知対象になる）。
     */
    suspend fun load(): Map<String, String>

    /**
     * 通知したプラグインを台帳に記録する
     *
     * 既存の記録に [notified] を上書きマージし、[managedPlugins] に含まれないプラグインの記録は
     * 捨てる（mpm.json から外れたプラグインの記録を残し続けないため）。
     *
     * @param notified 今回通知した「プラグイン名 -> 通知時の最新バージョン（raw）」
     * @param managedPlugins mpm.json に記載されているプラグイン名
     * @return 失敗した場合は理由
     */
    suspend fun record(
        notified: Map<String, String>,
        managedPlugins: Set<String>
    ): Either<String, Unit>
}