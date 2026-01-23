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
import party.morino.mpm.api.application.plugin.PluginUpdateService
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * プラグイン更新コマンドのコントローラー
 * プレゼンテーション層とユースケース層の橋渡しを行う
 * mpm update - 新しいバージョンがあるプラグインを更新
 */
@Command("mpm")
@CommandPermission("mpm.command")
class UpdateCommand : KoinComponent {
    // Koinによる依存性注入
    private val updateService: PluginUpdateService by inject()

    /**
     * 新しいバージョンがあるプラグインを更新するコマンド
     * @param sender コマンド送信者
     */
    @Subcommand("update")
    suspend fun update(sender: CommandSender) {
        sender.sendRichMessage("<gray>プラグインの更新を確認しています...</gray>")

        // PluginUpdateServiceを実行
        updateService.update().fold(
            // 失敗時の処理
            { error ->
                sender.sendRichMessage("<red>${error.message}</red>")
            },
            // 成功時の処理
            { updateResults ->
                if (updateResults.isEmpty()) {
                    sender.sendRichMessage("<yellow>更新対象のプラグインはありませんでした。</yellow>")
                } else {
                    // 成功と失敗を分ける
                    val successResults = updateResults.filter { it.success }
                    val failedResults = updateResults.filter { !it.success }

                    // 成功した更新を表示
                    if (successResults.isNotEmpty()) {
                        sender.sendRichMessage("<green>以下のプラグインを更新しました:</green>")
                        successResults.forEach { result ->
                            sender.sendRichMessage(
                                "  ✓ ${result.pluginName}: ${result.oldVersion} → ${result.newVersion}"
                            )
                        }
                    }

                    // 失敗した更新を表示
                    if (failedResults.isNotEmpty()) {
                        sender.sendRichMessage("<red>以下のプラグインの更新に失敗しました:</red>")
                        failedResults.forEach { result ->
                            sender.sendRichMessage("  ✗ ${result.pluginName}: ${result.errorMessage}")
                        }
                    }

                    sender.sendRichMessage("<gray>変更を反映するには、サーバーを再起動してください。</gray>")
                }
            }
        )
    }
}