/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.command.manage

import org.bukkit.command.CommandSender
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.core.backup.ServerBackupManager
import party.morino.mpm.api.model.backup.BackupReason
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission
import java.text.DecimalFormat

/**
 * バックアップコマンドのコントローラー
 * plugins/ディレクトリ全体のバックアップ・リストア機能を提供
 */
@Command("mpm", "mpm backup")
@CommandPermission("mpm.command")
class BackupCommand : KoinComponent {
    // KoinによるDI
    private val backupManager: ServerBackupManager by inject()

    /**
     * plugins/全体の手動バックアップを作成するコマンド
     * @param sender コマンド送信者
     */
    @Subcommand("backup create")
    suspend fun create(sender: CommandSender) {
        sender.sendRichMessage("<gray>バックアップを作成中...")

        backupManager.createBackup(BackupReason.MANUAL).fold(
            // 失敗時の処理
            { errorMessage ->
                sender.sendRichMessage("<red>$errorMessage")
            },
            // 成功時の処理
            { backupInfo ->
                val sizeFormatted = formatFileSize(backupInfo.sizeBytes)
                sender.sendRichMessage("<green>バックアップを作成しました")
                sender.sendRichMessage("<gray>  ID: <white>${backupInfo.id}")
                sender.sendRichMessage("<gray>  ファイル: <white>${backupInfo.fileName}")
                sender.sendRichMessage("<gray>  サイズ: <white>$sizeFormatted")
                sender.sendRichMessage("<gray>  プラグイン数: <white>${backupInfo.pluginsIncluded.size}")
            }
        )
    }

    /**
     * バックアップ一覧を表示するコマンド
     * @param sender コマンド送信者
     */
    @Subcommand("backup list")
    fun list(sender: CommandSender) {
        backupManager.listBackups().fold(
            // 失敗時の処理
            { errorMessage ->
                sender.sendRichMessage("<red>$errorMessage")
            },
            // 成功時の処理
            { backups ->
                if (backups.isEmpty()) {
                    sender.sendRichMessage("<gray>バックアップはありません。")
                    return@fold
                }

                sender.sendRichMessage("<yellow>バックアップ一覧:")
                for (backup in backups) {
                    val sizeFormatted = formatFileSize(backup.sizeBytes)
                    val reasonLabel =
                        when (backup.reason) {
                            BackupReason.UPDATE -> "[UPDATE]"
                            BackupReason.INSTALL -> "[INSTALL]"
                            BackupReason.MANUAL -> "[MANUAL]"
                        }
                    sender.sendRichMessage(
                        "<gray>  <white>${backup.id}<gray> - " +
                            "<aqua>$reasonLabel <gray>- " +
                            "<white>${backup.createdAt} <gray>- " +
                            "<white>$sizeFormatted"
                    )
                }
            }
        )
    }

    /**
     * バックアップをリストアするコマンド
     * @param sender コマンド送信者
     * @param backupId リストアするバックアップのID
     */
    @Subcommand("backup restore")
    suspend fun restore(
        sender: CommandSender,
        backupId: String
    ) {
        sender.sendRichMessage("<yellow>警告: リストアを実行すると、現在のplugins/ディレクトリの内容が上書きされます。")
        sender.sendRichMessage("<gray>リストア中...")

        backupManager.restore(backupId).fold(
            // 失敗時の処理
            { errorMessage ->
                sender.sendRichMessage("<red>$errorMessage")
            },
            // 成功時の処理
            { result ->
                sender.sendRichMessage("<green>リストアが完了しました")
                sender.sendRichMessage("<gray>  リストアしたプラグイン: <white>${result.restoredPlugins.size}個")
                sender.sendRichMessage("<gray>  リストアした設定フォルダ: <white>${result.restoredConfigs.size}個")
                sender.sendRichMessage("<yellow>変更を反映するには、サーバーを再起動してください。")
            }
        )
    }

    /**
     * バックアップを削除するコマンド
     * @param sender コマンド送信者
     * @param backupId 削除するバックアップのID
     */
    @Subcommand("backup delete")
    suspend fun delete(
        sender: CommandSender,
        backupId: String
    ) {
        backupManager.deleteBackup(backupId).fold(
            // 失敗時の処理
            { errorMessage ->
                sender.sendRichMessage("<red>$errorMessage")
            },
            // 成功時の処理
            {
                sender.sendRichMessage("<green>バックアップを削除しました: $backupId")
            }
        )
    }

    /**
     * 古いバックアップを削除するコマンド
     * @param sender コマンド送信者
     */
    @Subcommand("backup cleanup")
    suspend fun cleanup(sender: CommandSender) {
        sender.sendRichMessage("<gray>古いバックアップを削除中...")

        backupManager.cleanupOldBackups().fold(
            // 失敗時の処理
            { errorMessage ->
                sender.sendRichMessage("<red>$errorMessage")
            },
            // 成功時の処理
            { deletedCount ->
                if (deletedCount == 0) {
                    sender.sendRichMessage("<gray>削除が必要なバックアップはありませんでした。")
                } else {
                    sender.sendRichMessage("<green>$deletedCount 個のバックアップを削除しました。")
                }
            }
        )
    }

    /**
     * ファイルサイズを人間が読みやすい形式にフォーマットする
     * @param bytes バイト数
     * @return フォーマットされた文字列
     */
    private fun formatFileSize(bytes: Long): String {
        val df = DecimalFormat("#.##")
        return when {
            bytes >= 1_073_741_824 -> "${df.format(bytes / 1_073_741_824.0)} GB"
            bytes >= 1_048_576 -> "${df.format(bytes / 1_048_576.0)} MB"
            bytes >= 1_024 -> "${df.format(bytes / 1_024.0)} KB"
            else -> "$bytes B"
        }
    }
}