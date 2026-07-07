/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.downloader.spigot.data

import kotlinx.serialization.Serializable

/**
 * Spiget検索APIのリソース情報
 * example: https://api.spiget.org/v2/search/resources/<query>?fields=id,name,tag,downloads
 */
@Serializable
data class SpigotSearchResult(
    // リソースID
    val id: Long,
    // リソース名
    val name: String? = null,
    // 概要（タグ）
    val tag: String? = null,
    // ダウンロード数
    val downloads: Long? = null
)