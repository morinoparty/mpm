/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth.model.outdated

import kotlinx.serialization.Serializable
import party.morino.mpm.api.application.model.outdated.OutdatedCheckResult

/**
 * 更新チェック全体の結果レスポンス
 *
 * チェックに成功したプラグイン（[outdated]）と失敗したプラグイン（[errors]）を
 * 1つのオブジェクトにまとめて返す。従来は成功分の配列のみを返していたため、
 * チェックに失敗したプラグインがクライアントから完全に見えなくなっていた。
 *
 * @property outdated チェックに成功したプラグインの更新情報（`needsUpdate` で要更新かを判定する）
 * @property errors チェックに失敗したプラグインのエラー情報
 */
@Serializable
data class OutdatedCheckResponse(
    val outdated: List<OutdatedPluginResponse>,
    val errors: List<PluginCheckErrorResponse>
) {
    companion object {
        /**
         * OutdatedCheckResultから変換する
         */
        fun from(result: OutdatedCheckResult): OutdatedCheckResponse =
            OutdatedCheckResponse(
                outdated = result.outdatedPlugins.map { OutdatedPluginResponse.from(it) },
                errors = result.errors.map { PluginCheckErrorResponse.from(it) }
            )
    }
}