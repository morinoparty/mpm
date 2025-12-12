/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.commands.manage

import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.core.plugin.RemovePluginUseCase
import party.morino.mpm.api.core.plugin.RemoveUnmanagedUseCase

/**
 * プラグイン削除コマンドのコントローラー
 * プレゼンテーション層とユースケース層の橋渡しを行う
 * mpm remove <pluginName> - プラグインを管理対象から除外（ファイルは削除しない）
 */
@Command("mpm")
@Permission("mpm.command")
class RemoveCommand : KoinComponent {
    // Koinによる依存性注入
    private val removePluginUseCase: RemovePluginUseCase by inject()
    private val removeUnmanagedUseCase: RemoveUnmanagedUseCase by inject()

    /**
     * プラグインを管理対象から除外するコマンド
     * @param sender コマンド送信者
     * @param pluginName プラグイン名
     */
    @Command("remove <pluginName>")
    suspend fun remove(
        sender: CommandSender,
        @Argument("pluginName") pluginName: String
    ) {
        // ユースケースを実行
        removePluginUseCase.removePlugin(pluginName).fold(
            // 失敗時の処理
            { errorMessage ->
                sender.sendRichMessage("<red>$errorMessage</red>")
            },
            // 成功時の処理
            {
                sender.sendRichMessage("<green>プラグイン '$pluginName' を管理対象から除外しました。</green>")
                sender.sendRichMessage("<gray>プラグインファイルは削除されていません。</gray>")
                sender.sendRichMessage("<gray>ファイルも削除する場合は 'mpm uninstall $pluginName' を実行してください。</gray>")
            }
        )
    }

    /*
     * mpm管理下にないプラグインを削除するコマンド
     * @param sender コマンド送信者
     */
    @Command("removeUnmanaged")
    suspend fun removeUnmanaged(sender: CommandSender) {
        sender.sendRichMessage("<gray>管理外のプラグインを検索しています...</gray>")

        // ユースケースを実行
        removeUnmanagedUseCase.removeUnmanaged().fold(
            // 失敗時の処理
            { errorMessage ->
                sender.sendRichMessage("<red>$errorMessage</red>")
            },
            // 成功時の処理
            { removedPlugins ->
                if (removedPlugins.isEmpty()) {
                    sender.sendRichMessage("<yellow>削除対象のプラグインはありませんでした。</yellow>")
                } else {
                    sender.sendRichMessage("<green>以下のプラグインを削除しました:</green>")
                    removedPlugins.forEach { pluginName ->
                        sender.sendRichMessage("  - $pluginName")
                    }
                    sender.sendRichMessage("<gray>変更を反映するには、サーバーを再起動してください。</gray>")
                }
            }
        )
    }
}