/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth.model

import kotlinx.serialization.Serializable
import party.morino.mpm.api.application.model.outdated.OutdatedInfo

/**
 * 更新可能プラグインの情報レスポンス
 */
@Serializable
data class OutdatedPluginResponse(
    // プラグイン名
    val name: String,
    // 現在のバージョン
    val currentVersion: String,
    // 利用可能な最新バージョン
    val latestVersion: String
) {
    companion object {
        /**
         * OutdatedInfoから変換する
         */
        fun from(info: OutdatedInfo): OutdatedPluginResponse =
            OutdatedPluginResponse(
                name = info.pluginName,
                currentVersion = info.currentVersion,
                latestVersion = info.latestVersion
            )
    }
}