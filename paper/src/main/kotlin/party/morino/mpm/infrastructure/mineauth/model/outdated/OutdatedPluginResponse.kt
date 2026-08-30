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
import party.morino.mpm.api.application.model.outdated.OutdatedInfo

/**
 * 更新チェックが成功したプラグイン1件分の情報レスポンス
 *
 * @property name プラグイン名
 * @property currentVersion 現在のバージョン
 * @property latestVersion 利用可能な最新バージョン
 * @property needsUpdate 更新が必要かどうか（正規化済みバージョンで比較した結果）
 */
@Serializable
data class OutdatedPluginResponse(
    val name: String,
    val currentVersion: String,
    val latestVersion: String,
    val needsUpdate: Boolean
) {
    companion object {
        /**
         * OutdatedInfoから変換する
         */
        fun from(info: OutdatedInfo): OutdatedPluginResponse =
            OutdatedPluginResponse(
                name = info.pluginName,
                currentVersion = info.currentVersion,
                latestVersion = info.latestVersion,
                needsUpdate = info.needsUpdate
            )
    }
}