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
import party.morino.mpm.api.domain.plugin.dto.ManagedPluginDto
import party.morino.mpm.api.domain.plugin.dto.version.HistoryEntryDto

/**
 * `metadata/<plugin>.yaml` の内容を公開するレスポンス
 *
 * インストール履歴・sha256・ダウンロード元といった、詳細ページが必要とする情報をまとめて返す。
 *
 * @property name プラグイン名
 * @property description プラグインの説明
 * @property author プラグインの作者
 * @property website プラグインのウェブサイト
 * @property repository ダウンロード元リポジトリ（不明な場合は null）
 * @property currentVersion 現在インストールされているバージョン（raw表記）
 * @property latestVersion 記録されている最新バージョン（raw表記）
 * @property lastChecked 最終チェック日時（未記録の場合は null）
 * @property download ダウンロード情報
 * @property isLocked バージョンロック中かどうか
 * @property history インストール履歴（古い順）
 */
@Serializable
data class PluginMetadataResponse(
    val name: String,
    val description: String?,
    val author: String?,
    val website: String?,
    val repository: PluginRepositoryResponse?,
    val currentVersion: String,
    val latestVersion: String,
    val lastChecked: String?,
    val download: PluginDownloadResponse,
    val isLocked: Boolean,
    val history: List<HistoryEntryDto>
) {
    companion object {
        /**
         * 永続化用DTOの [ManagedPluginDto] から変換する
         */
        fun from(dto: ManagedPluginDto): PluginMetadataResponse =
            PluginMetadataResponse(
                name = dto.pluginInfo.name,
                description = dto.pluginInfo.description,
                author = dto.pluginInfo.author,
                website = dto.pluginInfo.website,
                repository = PluginRepositoryResponse.fromOrNull(dto.mpmInfo.repository),
                currentVersion = dto.mpmInfo.version.current.raw,
                latestVersion = dto.mpmInfo.version.latest.raw,
                // メタデータ未チェック時は空文字が入るため、意味のない空文字は null に落とす
                lastChecked =
                    dto.mpmInfo.version.lastChecked
                        .ifBlank { null },
                download = PluginDownloadResponse.from(dto.mpmInfo.download),
                isLocked = dto.mpmInfo.settings.lock ?: false,
                history = dto.mpmInfo.history
            )
    }
}