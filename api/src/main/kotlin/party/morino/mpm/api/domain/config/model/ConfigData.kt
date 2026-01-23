/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.config.model

import kotlinx.serialization.Serializable

/**
 * config.json ファイルのデータ構造
 *
 * リポジトリソースやグローバル設定を含む
 */
@Serializable
data class ConfigData(
    // リポジトリソースのリスト（優先順位順：先頭から順に検索）
    val repositories: List<RepositorySourceConfig> =
        listOf(
            RepositorySourceConfig.Local()
        ),

    // グローバル設定
    val settings: GlobalSettings = GlobalSettings(),
)
