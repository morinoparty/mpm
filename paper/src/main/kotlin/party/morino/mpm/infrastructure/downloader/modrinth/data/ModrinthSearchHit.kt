/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.downloader.modrinth.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modrinth検索結果の1件分の情報
 * @param slug プロジェクトのslug
 * @param title プロジェクトタイトル
 * @param description プロジェクトの説明
 * @param downloads ダウンロード数
 * @param projectType プロジェクトタイプ（"plugin", "mod"など、URL生成に使用）
 * example: https://api.modrinth.com/v2/search?query={query}&limit={limit}
 */
@Serializable
data class ModrinthSearchHit(
    val slug: String,
    val title: String,
    val description: String? = null,
    val downloads: Long? = null,
    @SerialName("project_type")
    val projectType: String? = null
)