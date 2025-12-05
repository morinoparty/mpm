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
import party.morino.mpm.api.core.plugin.AddPluginUseCase

/**
 * プラグイン追加コマンドのコントローラー
 * プレゼンテーション層とユースケース層の橋渡しを行う
 * mpm add <pluginName> - プラグインを管理対象に追加
 */
@Command("mpm")
@Permission("mpm.command")
class AddCommand : KoinComponent {
    // KoinによるDI
    private val addPluginUseCase: AddPluginUseCase by inject()

    /**
     * プラグインを管理対象に追加するコマンド
     * @param sender コマンド送信者
     * @param pluginName プラグイン名
     */
    @Command("add <pluginName>")
    suspend fun add(
        sender: CommandSender,
        @Argument("pluginName") pluginName: String
    ) {
        // ユースケースを実行
        addPluginUseCase.addPlugin(pluginName).fold(
            // 失敗時の処理
            { errorMessage ->
                sender.sendMessage(
                    Component.text(errorMessage, NamedTextColor.RED)
                )
            },
            // 成功時の処理
            {
                sender.sendMessage(
                    Component.text("プラグイン '$pluginName' をダウンロードして管理対象に追加しました。", NamedTextColor.GREEN)
                )
                sender.sendMessage(
                    Component.text("変更を反映するには、サーバーを再起動してください。", NamedTextColor.GRAY)
                )
            }
        )
    }
}