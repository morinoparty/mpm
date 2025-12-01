/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.modrinth.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modrinthバージョン情報
 * @param id バージョンID
 * @param versionNumber バージョン番号
 * @param files ファイルリスト
 * example: https://api.modrinth.com/v2/project/quickshop-hikari/version?loaders=["paper", "spigot"]
 */
@Serializable
data class ModrinthVersion(
    val id: String,
    @SerialName("version_number")
    val versionNumber: String,
    val files: List<ModrinthVersionFile>
)