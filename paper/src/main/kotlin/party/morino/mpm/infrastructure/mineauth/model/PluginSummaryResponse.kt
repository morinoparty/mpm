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
import party.morino.mpm.api.domain.plugin.model.ManagedPlugin

/**
 * プラグイン一覧レスポンスの各エントリ
 */
@Serializable
data class PluginSummaryResponse(
    // プラグイン名
    val name: String,
    // 現在インストールされているバージョン
    val currentVersion: String,
    // リポジトリ上の最新バージョン
    val latestVersion: String,
    // バージョン固定（lock）状態
    val isLocked: Boolean,
    // 更新可能かどうか
    val isOutdated: Boolean,
    // 説明文
    val description: String?
) {
    companion object {
        /**
         * ManagedPluginドメインモデルからレスポンスDTOを生成する
         */
        fun from(plugin: ManagedPlugin): PluginSummaryResponse =
            PluginSummaryResponse(
                name = plugin.name.value,
                currentVersion = plugin.currentVersion.raw,
                latestVersion = plugin.latestVersion.raw,
                isLocked = plugin.isLocked,
                isOutdated = plugin.isOutdated(),
                description = plugin.description
            )
    }
}