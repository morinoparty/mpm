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
import party.morino.mpm.api.core.plugin.LockPluginUseCase
import party.morino.mpm.api.core.plugin.UnlockPluginUseCase

/**
 * プラグインロック/アンロックコマンドのコントローラー
 * プレゼンテーション層とユースケース層の橋渡しを行う
 * mpm lock <plugin> - プラグインをロックして自動更新を防ぐ
 * mpm unlock <plugin> - プラグインのロックを解除
 */
@Command("mpm")
@Permission("mpm.command")
class LockCommand : KoinComponent {
    // Koinによる依存性注入
    private val lockPluginUseCase: LockPluginUseCase by inject()
    private val unlockPluginUseCase: UnlockPluginUseCase by inject()

    /**
     * プラグインをロックするコマンド
     * @param sender コマンド送信者
     * @param plugin プラグイン名
     */
    @Command("lock <plugin>")
    suspend fun lock(
        sender: CommandSender,
        @Argument("plugin") plugin: String
    ) {
        // ユースケースを実行
        lockPluginUseCase.lockPlugin(plugin).fold(
            // 失敗時の処理
            { errorMessage ->
                sender.sendMessage(
                    Component.text(errorMessage, NamedTextColor.RED)
                )
            },
            // 成功時の処理
            {
                sender.sendMessage(
                    Component.text("プラグイン '$plugin' をロックしました。", NamedTextColor.GREEN)
                )
                sender.sendMessage(
                    Component.text("このプラグインは自動更新されません。", NamedTextColor.GRAY)
                )
            }
        )
    }

    /**
     * プラグインのロックを解除するコマンド
     * @param sender コマンド送信者
     * @param plugin プラグイン名
     */
    @Command("unlock <plugin>")
    suspend fun unlock(
        sender: CommandSender,
        @Argument("plugin") plugin: String
    ) {
        // ユースケースを実行
        unlockPluginUseCase.unlockPlugin(plugin).fold(
            // 失敗時の処理
            { errorMessage ->
                sender.sendMessage(
                    Component.text(errorMessage, NamedTextColor.RED)
                )
            },
            // 成功時の処理
            {
                sender.sendMessage(
                    Component.text("プラグイン '$plugin' のロックを解除しました。", NamedTextColor.GREEN)
                )
                sender.sendMessage(
                    Component.text("このプラグインは自動更新の対象になります。", NamedTextColor.GRAY)
                )
            }
        )
    }
}