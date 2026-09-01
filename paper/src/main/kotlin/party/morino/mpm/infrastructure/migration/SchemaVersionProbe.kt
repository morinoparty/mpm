/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.migration

import kotlinx.serialization.Serializable
import party.morino.mpm.api.domain.migration.SchemaVersions

/**
 * ファイルから schemaVersion だけを先読み（probe）するための最小 DTO
 *
 * 本来の型（ManagedPluginDto）で読んでしまうと、未来の版数で増えたフィールドにより
 * strictMode を有効にした `Yaml.default` が例外を投げてしまう。
 * そのため「版数の先読み専用」に、未知フィールドを無視する Yaml と組で使う。
 *
 * YAML だけでなく JSON（mpm.json / config.json）の先読みにも同じ DTO を使う。
 * `Utils.json` は `ignoreUnknownKeys = true` のため、そのままデコードできる。
 * 実際の利用箇所は [SchemaMigratorImpl] と [SchemaVersionGuard]。
 */
@Serializable
data class SchemaVersionProbe(
    val schemaVersion: Int = SchemaVersions.LEGACY
)