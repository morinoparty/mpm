/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

import party.morino.mpm.api.application.model.UpdateResult

/**
 * 連動更新（sync:）で失敗した子プラグインの要約メッセージを組み立てる純粋関数
 *
 * バージョン切り替えの戻り値は親1件のみのため、子の失敗をログに出すだけでは
 * 呼び出し側（コマンド・HTTP API）が「親だけ切り替わり、子が旧バージョンのまま」という
 * `sync:` の不変条件が崩れた状態に気付けない。親の結果の `errorMessage` に載せて可視化する。
 *
 * @param syncResults 連動更新の結果一覧
 * @return 失敗した子がある場合はその要約メッセージ、すべて成功（または対象なし）の場合はnull
 */
internal fun buildSyncFailureMessage(syncResults: List<UpdateResult>): String? {
    val failed = syncResults.filter { !it.success }
    if (failed.isEmpty()) return null

    // どの子がどんな理由で失敗したかを列挙する（原因不明でもプラグイン名は必ず出す）
    val details = failed.joinToString(", ") { "${it.pluginName}: ${it.errorMessage ?: "原因不明"}" }
    return "親のバージョン切り替えは完了しましたが、連動更新に失敗した sync: プラグインがあります（$details）。" +
        "バージョンが食い違ったままのため、手動で確認してください。"
}