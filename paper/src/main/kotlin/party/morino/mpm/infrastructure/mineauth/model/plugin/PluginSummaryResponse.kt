/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth.model.plugin

import kotlinx.serialization.Serializable
import party.morino.mpm.api.domain.plugin.model.ManagedPlugin
import party.morino.mpm.api.domain.plugin.model.PluginEntryStatus

/**
 * プラグイン一覧レスポンスの各エントリ
 *
 * 管理下プラグインだけでなく、`filter=unmanaged` で返る管理外プラグインや、
 * メタデータを読み込めなかったプラグインも同じ形で表現する。
 *
 * @property name プラグイン名
 * @property status エントリの由来（MANAGED / UNMANAGED / METADATA_UNAVAILABLE）
 * @property isManaged mpmの管理下にあるかどうか（status が MANAGED のときのみ true）
 * @property currentVersion 現在インストールされているバージョン（不明な場合は null）
 * @property latestVersion リポジトリ上の最新バージョン（不明な場合は null）
 * @property isLocked バージョン固定（lock）状態
 * @property isOutdated 更新可能かどうか
 * @property description 説明文
 * @property author 作者
 * @property repository ダウンロード元リポジトリ（不明な場合は null）
 * @property lastChecked 最終チェック日時（未記録の場合は null）
 */
@Serializable
data class PluginSummaryResponse(
    val name: String,
    val status: String,
    val isManaged: Boolean,
    val currentVersion: String?,
    val latestVersion: String?,
    val isLocked: Boolean,
    val isOutdated: Boolean,
    val description: String?,
    val author: String?,
    val repository: PluginRepositoryResponse?,
    val lastChecked: String?
) {
    companion object {
        /**
         * ManagedPluginドメインモデルからレスポンスDTOを生成する
         *
         * 管理外・メタデータ欠損のエントリは `"unmanaged"` / `"unknown"` という
         * センチネル文字列をバージョン欄に持つ。これをそのままクライアントへ渡すと
         * 実在するバージョン名と区別できないため、null に変換して「不明」を明示する。
         */
        fun from(plugin: ManagedPlugin): PluginSummaryResponse {
            // メタデータを持つ（＝バージョンが実在する）エントリかどうか
            val hasMetadata = plugin.status == PluginEntryStatus.MANAGED
            return PluginSummaryResponse(
                name = plugin.name.value,
                status = plugin.status.name,
                isManaged = hasMetadata,
                currentVersion = plugin.currentVersion.raw.takeIf { hasMetadata },
                latestVersion = plugin.latestVersion.raw.takeIf { hasMetadata },
                isLocked = plugin.isLocked,
                isOutdated = plugin.isOutdated(),
                description = plugin.description,
                author = plugin.author,
                repository = PluginRepositoryResponse.fromOrNull(plugin.repository),
                // プレースホルダは lastChecked が空文字になるため null に落とす
                lastChecked = plugin.lastChecked.ifBlank { null }
            )
        }
    }
}