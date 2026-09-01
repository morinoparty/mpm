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
 * 1ファイルに対するスキーママイグレーションの結果
 *
 * 「触っていない理由」まで型で表現することで、
 * 呼び出し側がログの文言を出し分けられるようにしている。
 * すべてのケースがログ表示用のファイル名 fileName を持つ。
 */
sealed class SchemaMigrationOutcome {
    abstract val fileName: String

    /** ファイルが存在しなかった（マイグレーション不要） */
    data class Absent(
        override val fileName: String
    ) : SchemaMigrationOutcome()

    /** 既に現行版数だったため一切書き込まなかった（冪等性を担保する分岐） */
    data class AlreadyCurrent(
        override val fileName: String
    ) : SchemaMigrationOutcome()

    /**
     * マイグレーションを実行して書き込んだ
     *
     * @property fileName 対象ファイル名
     * @property fromVersion 変換前の版数
     * @property toVersion 変換後の版数（＝現行版数）
     */
    data class Migrated(
        override val fileName: String,
        val fromVersion: Int,
        val toVersion: Int
    ) : SchemaMigrationOutcome()

    /**
     * 現行版数より新しいファイル。ダウングレードはできないため触れていない
     *
     * @property fileName 対象ファイル名
     * @property foundVersion ファイルに記載されていた版数
     */
    data class FutureVersion(
        override val fileName: String,
        val foundVersion: Int
    ) : SchemaMigrationOutcome()

    /**
     * 読み込み・変換・書き込みのいずれかに失敗した（起動は継続する）
     *
     * @property fileName 対象ファイル名
     * @property reason 失敗理由（ログ表示用）
     */
    data class Failed(
        override val fileName: String,
        val reason: String
    ) : SchemaMigrationOutcome()
}