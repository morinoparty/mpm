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
import party.morino.mpm.api.application.plugin.PluginLifecycleService
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.model.plugin.InstalledPlugin
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * プラグイン削除コマンドのコントローラー
 * プレゼンテーション層とユースケース層の橋渡しを行う
 * mpm remove <pluginName> - プラグインを管理対象から除外（ファイルは削除しない）
 */
@Command("mpm")
@CommandPermission("mpm.command")
class RemoveCommand : KoinComponent {
    // Koinによる依存性注入
    private val lifecycleService: PluginLifecycleService by inject()

    /**
     * プラグインを管理対象から除外するコマンド
     * @param sender コマンド送信者
     * @param plugin インストール済みプラグイン
     */
    @Subcommand("remove")
    suspend fun remove(
        sender: CommandSender,
        plugin: InstalledPlugin
    ) {
        val pluginId = plugin.pluginId
        // PluginLifecycleServiceを実行
        lifecycleService.remove(PluginName(pluginId)).fold(
            // 失敗時の処理
            { error ->
                sender.sendRichMessage("<red>${error.message}</red>")
            },
            // 成功時の処理
            {
                sender.sendRichMessage("<green>プラグイン '$pluginId' を管理対象から除外しました。</green>")
                sender.sendRichMessage("<gray>プラグインファイルは削除されていません。</gray>")
                sender.sendRichMessage("<gray>ファイルも削除する場合は 'mpm uninstall $pluginId' を実行してください。</gray>")
            }
        )
    }

    /*
     * mpm管理下にないプラグインを削除するコマンド
     * @param sender コマンド送信者
     */
    @Subcommand("removeUnmanaged")
    suspend fun removeUnmanaged(sender: CommandSender) {
        sender.sendRichMessage("<gray>管理外のプラグインを検索しています...</gray>")

        // PluginLifecycleServiceを実行
        lifecycleService.removeUnmanaged().fold(
            // 失敗時の処理
            { error ->
                sender.sendRichMessage("<red>${error.message}</red>")
            },
            // 成功時の処理
            { removedCount ->
                if (removedCount == 0) {
                    sender.sendRichMessage("<yellow>削除対象のプラグインはありませんでした。</yellow>")
                } else {
                    sender.sendRichMessage("<green>${removedCount}個のプラグインを削除しました。</green>")
                    sender.sendRichMessage("<gray>変更を反映するには、サーバーを再起動してください。</gray>")
                }
            }
        )
    }
}