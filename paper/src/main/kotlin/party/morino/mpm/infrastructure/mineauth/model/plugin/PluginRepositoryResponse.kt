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
import party.morino.mpm.api.domain.downloader.model.RepositoryType
import party.morino.mpm.api.domain.plugin.dto.RepositoryInfo

/**
 * プラグインのダウンロード元リポジトリを表すレスポンス
 *
 * ドメインの [RepositoryInfo] は enum を持つため、HTTP 表現では文字列に落として公開する。
 *
 * @property type リポジトリ種別（GITHUB / SPIGOTMC / HANGAR / MODRINTH）
 * @property id リポジトリ固有のID（GitHubなら owner/repo など）
 */
@Serializable
data class PluginRepositoryResponse(
    val type: String,
    val id: String
) {
    companion object {
        /**
         * [RepositoryInfo] から変換する
         *
         * 管理外プラグインやメタデータ欠損時は [RepositoryType.UNKNOWN] のダミー値が入るため、
         * センチネルを素通しせず null を返して「不明」であることを明示する。
         *
         * @param info ドメインのリポジトリ情報
         * @return リポジトリ情報。種別が不明な場合は null
         */
        fun fromOrNull(info: RepositoryInfo): PluginRepositoryResponse? =
            if (info.type == RepositoryType.UNKNOWN) {
                null
            } else {
                PluginRepositoryResponse(type = info.type.name, id = info.id)
            }
    }
}