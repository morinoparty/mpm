/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.downloader.hangar.data

import kotlinx.serialization.Serializable

/**
 * Hangarプロジェクトの個別リンク（settings.links[].links[]の要素）
 * @param name リンク名（例: "Homepage", "Issues", "Source"）
 * @param url リンク先URL（未設定の場合はnull）
 */
@Serializable
data class HangarProjectLink(
    val name: String? = null,
    val url: String? = null
)