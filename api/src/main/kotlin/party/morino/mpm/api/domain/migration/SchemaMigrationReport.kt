/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.migration

/**
 * スキーママイグレーション全体の結果レポート
 *
 * @property outcomes 対象ファイルごとの結果
 */
data class SchemaMigrationReport(
    val outcomes: List<SchemaMigrationOutcome>
) {
    /** 実際にマイグレートしたファイル数 */
    val migratedCount: Int
        get() = outcomes.count { it is SchemaMigrationOutcome.Migrated }

    /** 失敗したファイル（起動は止めず警告ログに留める対象） */
    val failures: List<SchemaMigrationOutcome.Failed>
        get() = outcomes.filterIsInstance<SchemaMigrationOutcome.Failed>()

    /** 現行版数より新しいため見送ったファイル */
    val futures: List<SchemaMigrationOutcome.FutureVersion>
        get() = outcomes.filterIsInstance<SchemaMigrationOutcome.FutureVersion>()
}