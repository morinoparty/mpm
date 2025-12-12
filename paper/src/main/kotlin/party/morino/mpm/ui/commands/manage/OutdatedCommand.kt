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
import party.morino.mpm.api.core.plugin.CheckOutdatedUseCase

/**
 * プラグイン更新確認コマンドのコントローラー
 * プレゼンテーション層とユースケース層の橋渡しを行う
 * mpm outdated <plugin> - プラグインの更新を確認
 * mpm outdatedAll - すべてのプラグインの更新を確認
 */
@Command("mpm")
@Permission("mpm.command")
class OutdatedCommand : KoinComponent {
    // Koinによる依存性注入
    private val checkOutdatedUseCase: CheckOutdatedUseCase by inject()

    /**
     * 指定されたプラグインの更新を確認するコマンド
     * @param sender コマンド送信者
     * @param plugin プラグイン名
     */
    @Command("outdated <plugin>")
    suspend fun outdated(
        sender: CommandSender,
        @Argument("plugin") plugin: String
    ) {
        sender.sendMessage(
            Component.text("プラグイン '$plugin' の更新を確認しています...", NamedTextColor.GRAY)
        )

        // ユースケースを実行
        checkOutdatedUseCase.checkOutdated(plugin).fold(
            // 失敗時の処理
            { errorMessage ->
                sender.sendMessage(
                    Component.text(errorMessage, NamedTextColor.RED)
                )
            },
            // 成功時の処理
            { outdatedInfo ->
                if (outdatedInfo.needsUpdate) {
                    sender.sendMessage(
                        Component.text("プラグイン '${outdatedInfo.pluginName}' の更新があります:", NamedTextColor.YELLOW)
                    )
                    sender.sendMessage(
                        Component.text("  現在: ${outdatedInfo.currentVersion}", NamedTextColor.WHITE)
                    )
                    sender.sendMessage(
                        Component.text("  最新: ${outdatedInfo.latestVersion}", NamedTextColor.GREEN)
                    )
                    sender.sendMessage(
                        Component.text("更新するには 'mpm update' を実行してください。", NamedTextColor.GRAY)
                    )
                } else {
                    sender.sendMessage(
                        Component.text(
                            "プラグイン '${outdatedInfo.pluginName}' は最新です (${outdatedInfo.currentVersion})",
                            NamedTextColor.GREEN
                        )
                    )
                }
            }
        )
    }

    /**
     * すべての管理下プラグインの更新を確認するコマンド
     * @param sender コマンド送信者
     */
    @Command("outdatedAll")
    suspend fun outdatedAll(sender: CommandSender) {
        sender.sendMessage(
            Component.text("すべてのプラグインの更新を確認しています...", NamedTextColor.GRAY)
        )

        // ユースケースを実行
        checkOutdatedUseCase.checkAllOutdated().fold(
            // 失敗時の処理
            { errorMessage ->
                sender.sendMessage(
                    Component.text(errorMessage, NamedTextColor.RED)
                )
            },
            // 成功時の処理
            { outdatedInfoList ->
                // 更新が必要なプラグインのみフィルタリング
                val needsUpdateList = outdatedInfoList.filter { it.needsUpdate }

                if (needsUpdateList.isEmpty()) {
                    sender.sendMessage(
                        Component.text("すべてのプラグインは最新です。", NamedTextColor.GREEN)
                    )
                } else {
                    sender.sendMessage(
                        Component.text("以下のプラグインに更新があります:", NamedTextColor.YELLOW)
                    )
                    needsUpdateList.forEach { outdatedInfo ->
                        sender.sendMessage(
                            Component.text(
                                "  - ${outdatedInfo.pluginName}: ${outdatedInfo.currentVersion} → ${outdatedInfo.latestVersion}",
                                NamedTextColor.WHITE
                            )
                        )
                    }
                    sender.sendMessage(
                        Component.text("更新するには 'mpm update' を実行してください。", NamedTextColor.GRAY)
                    )
                }
            }
        )
    }
}