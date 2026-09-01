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
 * mpm が読み書きする設定ファイルのスキーマ版数を定義する定数オブジェクト
 *
 * mpm.json / config.json / metadata 配下の yaml の3ファイルで共通の版数体系を使う。
 * schemaVersion フィールドが存在しないファイルは v1（レガシー）として扱うため、
 * 各 DTO のデフォルト値は [LEGACY] にしてある。
 */
object SchemaVersions {
    /** 現行のスキーマ版数。書き込み時は常にこの値をスタンプする */
    const val CURRENT: Int = 2

    /** schemaVersion フィールドを持たないレガシーファイルに割り当てる版数 */
    const val LEGACY: Int = 1

    /** JSON / YAML 上のスキーマ版数フィールド名（3ファイルで共通） */
    const val FIELD_NAME: String = "schemaVersion"
}