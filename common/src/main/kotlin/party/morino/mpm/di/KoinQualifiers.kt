/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.di

/**
 * Koin に同じ型を複数登録する場合の名前（qualifier）
 *
 * 各プラットフォームのモジュールと共通ロジックで同じ名前を参照するため、定数として一箇所にまとめる。
 */
object KoinQualifiers {
    /** プラグインのデータディレクトリ（plugins/mpm）を表す File */
    const val DATA_DIRECTORY = "dataDirectory"
}