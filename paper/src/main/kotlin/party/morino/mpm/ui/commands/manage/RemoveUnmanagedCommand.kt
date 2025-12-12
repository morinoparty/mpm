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
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.core.plugin.RemoveUnmanagedUseCase

/**
 * 管理外プラグイン削除コマンドのコントローラー
 * プレゼンテーション層とユースケース層の橋渡しを行う
 * mpm removeUnmanaged - mpm管理下にないプラグインを削除
 */
@Command("mpm")
@Permission("mpm.command")
class RemoveUnmanagedCommand : KoinComponent {
    // Koinによる依存性注入
    private val removeUnmanagedUseCase: RemoveUnmanagedUseCase by inject()

    /**
     * mpm管理下にないプラグインを削除するコマンド
     * @param sender コマンド送信者
     */
    @Command("removeUnmanaged")
    suspend fun removeUnmanaged(sender: CommandSender) {
        sender.sendMessage(
            Component.text("管理外のプラグインを検索しています...", NamedTextColor.GRAY)
        )

        // ユースケースを実行
        removeUnmanagedUseCase.removeUnmanaged().fold(
            // 失敗時の処理
            { errorMessage ->
                sender.sendMessage(
                    Component.text(errorMessage, NamedTextColor.RED)
                )
            },
            // 成功時の処理
            { removedPlugins ->
                if (removedPlugins.isEmpty()) {
                    sender.sendMessage(
                        Component.text("削除対象のプラグインはありませんでした。", NamedTextColor.YELLOW)
                    )
                } else {
                    sender.sendMessage(
                        Component.text("以下のプラグインを削除しました:", NamedTextColor.GREEN)
                    )
                    removedPlugins.forEach { pluginName ->
                        sender.sendMessage(
                            Component.text("  - $pluginName", NamedTextColor.WHITE)
                        )
                    }
                    sender.sendMessage(
                        Component.text("変更を反映するには、サーバーを再起動してください。", NamedTextColor.GRAY)
                    )
                }
            }
        )
    }
}