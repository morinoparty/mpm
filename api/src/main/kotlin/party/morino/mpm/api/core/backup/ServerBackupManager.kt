/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
and related and neighboring rights to this software to the public domain worldwide.
This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.core.backup

import arrow.core.Either
import party.morino.mpm.api.model.backup.BackupReason
import party.morino.mpm.api.model.backup.RestoreResult
import party.morino.mpm.api.model.backup.ServerBackupInfo

/**
 * サーバー全体のバックアップを管理するインターフェース
 * plugins/ディレクトリ全体（jarファイル + 設定フォルダ）をZIP形式でバックアップする
 */
interface ServerBackupManager {
    /**
     * plugins/ディレクトリ全体のバックアップを作成する
     *
     * @param reason バックアップを作成する理由
     * @return 成功時はServerBackupInfo、失敗時はエラーメッセージ
     */
    suspend fun createBackup(reason: BackupReason): Either<String, ServerBackupInfo>

    /**
     * 指定されたバックアップからplugins/ディレクトリをリストアする
     *
     * @param backupId リストアするバックアップのID
     * @return 成功時はRestoreResult、失敗時はエラーメッセージ
     */
    suspend fun restore(backupId: String): Either<String, RestoreResult>

    /**
     * 存在するすべてのバックアップの一覧を取得する
     *
     * @return 成功時はServerBackupInfoのリスト、失敗時はエラーメッセージ
     */
    fun listBackups(): Either<String, List<ServerBackupInfo>>

    /**
     * 指定されたバックアップを削除する
     *
     * @param backupId 削除するバックアップのID
     * @return 成功時はUnit、失敗時はエラーメッセージ
     */
    suspend fun deleteBackup(backupId: String): Either<String, Unit>

    /**
     * 設定に基づいて古いバックアップを削除する
     *
     * @return 成功時は削除されたバックアップの数、失敗時はエラーメッセージ
     */
    suspend fun cleanupOldBackups(): Either<String, Int>
}