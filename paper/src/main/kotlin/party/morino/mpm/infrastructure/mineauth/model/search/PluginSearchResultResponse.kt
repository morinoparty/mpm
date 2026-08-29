/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth.model.search

import kotlinx.serialization.Serializable
import party.morino.mpm.api.domain.downloader.model.PluginSearchResult

/**
 * リポジトリ横断検索の1件分の結果レスポンス（`mpm search` 相当）
 *
 * @property source リポジトリ種別（MODRINTH / HANGAR / SPIGOTMC / GITHUB）
 * @property slug `mpm add` などで使用できるリポジトリ上の識別子
 * @property name 表示名
 * @property description 概要説明
 * @property downloads ダウンロード数
 * @property url プロジェクトページのURL
 */
@Serializable
data class PluginSearchResultResponse(
    val source: String,
    val slug: String,
    val name: String,
    val description: String?,
    val downloads: Long?,
    val url: String?
) {
    companion object {
        /**
         * PluginSearchResultから変換する
         */
        fun from(result: PluginSearchResult): PluginSearchResultResponse =
            PluginSearchResultResponse(
                source = result.source.name,
                slug = result.slug,
                name = result.name,
                description = result.description,
                downloads = result.downloads,
                url = result.url
            )
    }
}