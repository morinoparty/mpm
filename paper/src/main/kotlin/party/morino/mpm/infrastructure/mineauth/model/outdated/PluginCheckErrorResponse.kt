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
import party.morino.mpm.api.application.model.outdated.PluginCheckError

/**
 * 更新チェックに失敗したプラグイン1件分のエラーレスポンス
 *
 * チェックできなかったプラグインを「最新である」と誤って表示しないために公開する。
 *
 * @property name チェックに失敗したプラグイン名
 * @property errorMessage 失敗理由
 */
@Serializable
data class PluginCheckErrorResponse(
    val name: String,
    val errorMessage: String
) {
    companion object {
        /**
         * PluginCheckErrorから変換する
         */
        fun from(error: PluginCheckError): PluginCheckErrorResponse =
            PluginCheckErrorResponse(
                name = error.pluginName,
                errorMessage = error.errorMessage
            )
    }
}