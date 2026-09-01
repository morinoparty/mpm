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
 * 設定ファイル（mpm.json / config.json / metadata 配下の yaml）のスキーママイグレーションを行う
 *
 * 他のサービスが該当ファイルを読むより前に、プラグイン起動時に一度だけ実行される想定。
 * 1ファイルの失敗が他ファイルの処理やプラグインの起動全体を止めてはならない。
 */
interface SchemaMigrator {
    /**
     * 対象ファイルをすべて走査し、schemaVersion が [SchemaVersions.CURRENT] でないものをマイグレートする
     *
     * 例外は内部で捕捉するため、この関数は throw しない契約とする。
     *
     * @return マイグレーション結果のレポート
     */
    suspend fun migrateAll(): SchemaMigrationReport
}