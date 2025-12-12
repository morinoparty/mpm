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
import party.morino.mpm.api.core.plugin.UpdatePluginUseCase

/**
 * プラグイン更新コマンドのコントローラー
 * プレゼンテーション層とユースケース層の橋渡しを行う
 * mpm update - 新しいバージョンがあるプラグインを更新
 */
@Command("mpm")
@Permission("mpm.command")
class UpdateCommand : KoinComponent {
    // Koinによる依存性注入
    private val updatePluginUseCase: UpdatePluginUseCase by inject()

    /**
     * 新しいバージョンがあるプラグインを更新するコマンド
     * @param sender コマンド送信者
     */
    @Command("update")
    suspend fun update(sender: CommandSender) {
        sender.sendMessage(
            Component.text("プラグインの更新を確認しています...", NamedTextColor.GRAY)
        )

        // ユースケースを実行
        updatePluginUseCase.updatePlugins().fold(
            // 失敗時の処理
            { errorMessage ->
                sender.sendMessage(
                    Component.text(errorMessage, NamedTextColor.RED)
                )
            },
            // 成功時の処理
            { updateResults ->
                if (updateResults.isEmpty()) {
                    sender.sendMessage(
                        Component.text("更新対象のプラグインはありませんでした。", NamedTextColor.YELLOW)
                    )
                } else {
                    // 成功と失敗を分ける
                    val successResults = updateResults.filter { it.success }
                    val failedResults = updateResults.filter { !it.success }

                    // 成功した更新を表示
                    if (successResults.isNotEmpty()) {
                        sender.sendMessage(
                            Component.text("以下のプラグインを更新しました:", NamedTextColor.GREEN)
                        )
                        successResults.forEach { result ->
                            sender.sendMessage(
                                Component.text(
                                    "  ✓ ${result.pluginName}: ${result.oldVersion} → ${result.newVersion}",
                                    NamedTextColor.WHITE
                                )
                            )
                        }
                    }

                    // 失敗した更新を表示
                    if (failedResults.isNotEmpty()) {
                        sender.sendMessage(
                            Component.text("以下のプラグインの更新に失敗しました:", NamedTextColor.RED)
                        )
                        failedResults.forEach { result ->
                            sender.sendMessage(
                                Component.text("  ✗ ${result.pluginName}: ${result.errorMessage}", NamedTextColor.WHITE)
                            )
                        }
                    }

                    sender.sendMessage(
                        Component.text("変更を反映するには、サーバーを再起動してください。", NamedTextColor.GRAY)
                    )
                }
            }
        )
    }
}