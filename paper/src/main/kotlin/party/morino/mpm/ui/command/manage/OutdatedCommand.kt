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
import party.morino.mpm.api.application.plugin.PluginInfoService
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * プラグイン更新確認コマンドのコントローラー
 *
 * mpm outdated - すべての管理下プラグインの更新を確認
 */
@Command("mpm")
@CommandPermission("mpm.command.list")
class OutdatedCommand : KoinComponent {
    // Koinによる依存性注入
    private val infoService: PluginInfoService by inject()

    /**
     * すべての管理下プラグインの更新を確認するコマンド
     *
     * @param sender コマンド送信者
     */
    @Subcommand("outdated")
    @Description("すべてのプラグインの更新を確認します。")
    suspend fun outdated(sender: CommandSender) {
        sender.sendRichMessage("<gray>すべてのプラグインの更新を確認しています...</gray>")

        infoService.checkAllOutdated().fold(
            { error ->
                sender.sendRichMessage("<red>${error.message}</red>")
            },
            { result ->
                // チェックに失敗したプラグインを警告表示
                result.errors.forEach { checkError ->
                    sender.sendRichMessage(
                        "<red>${checkError.pluginName}: ${checkError.errorMessage}</red>"
                    )
                }

                // 更新が必要なプラグインのみフィルタリング
                val needsUpdateList = result.outdatedPlugins.filter { it.needsUpdate }

                if (needsUpdateList.isEmpty() && result.errors.isEmpty()) {
                    sender.sendRichMessage("<green>すべてのプラグインは最新です。</green>")
                } else {
                    if (needsUpdateList.isNotEmpty()) {
                        sender.sendRichMessage("<yellow>以下のプラグインに更新があります:</yellow>")
                        needsUpdateList.forEach { outdatedInfo ->
                            sender.sendRichMessage(
                                "  - ${outdatedInfo.pluginName}: ${outdatedInfo.currentVersion} → ${outdatedInfo.latestVersion}"
                            )
                        }
                        sender.sendRichMessage("<gray>更新するには 'mpm update' を実行してください。</gray>")
                    }
                }
            }
        )
    }
}