/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
 * and related and neighboring rights to this software to the public domain worldwide.
 * This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.plugin.dto

import kotlinx.serialization.Serializable
import party.morino.mpm.api.domain.migration.SchemaVersions

/**
 * metadata/<プラグイン名>.yaml のデータ構造
 */
@Serializable
data class ManagedPluginDto(
    // ファイルのスキーマ版数（フィールドが無いレガシーファイルは1として扱う）
    val schemaVersion: Int = SchemaVersions.LEGACY,
    val pluginInfo: PluginInfoDto,
    val mpmInfo: MpmInfoDto
)