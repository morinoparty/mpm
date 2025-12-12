/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.commands.manage

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.core.plugin.RemovePluginUseCase

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
                sender.sendMessage(
                    Component.text(errorMessage, NamedTextColor.RED)
                )
            },
            // 成功時の処理
            {
                sender.sendMessage(
                    Component.text("プラグイン '$pluginName' を管理対象から除外しました。", NamedTextColor.GREEN)
                )
                sender.sendMessage(
                    Component.text("プラグインファイルは削除されていません。", NamedTextColor.GRAY)
                )
                sender.sendMessage(
                    Component.text("ファイルも削除する場合は 'mpm uninstall $pluginName' を実行してください。", NamedTextColor.GRAY)
                )
            }
        )
    }
}