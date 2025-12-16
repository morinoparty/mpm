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
import party.morino.mpm.api.core.plugin.PluginInfoManager
import party.morino.mpm.api.model.plugin.InstalledPlugin
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * プラグイン更新確認コマンドのコントローラー
 * プレゼンテーション層とユースケース層の橋渡しを行う
 * mpm outdated <plugin> - プラグインの更新を確認
 * mpm outdatedAll - すべてのプラグインの更新を確認
 */
@Command("mpm")
@CommandPermission("mpm.command")
class OutdatedCommand : KoinComponent {
    // Koinによる依存性注入
    private val infoManager: PluginInfoManager by inject()

    @Subcommand("outdated")
    @Description("指定されたプラグインの更新を確認します。")
    suspend fun outdated(
        sender: CommandSender,
        plugin: InstalledPlugin
    ) {
        val pluginName = plugin.pluginId
        sender.sendRichMessage("<gray>プラグイン '$pluginName' の更新を確認しています...</gray>")

        // PluginInfoManagerを実行
        infoManager.checkOutdated(plugin).fold(
            // 失敗時の処理
            { errorMessage ->
                sender.sendRichMessage("<red>$errorMessage</red>")
            },
            // 成功時の処理
            { outdatedInfo ->
                if (outdatedInfo.needsUpdate) {
                    sender.sendRichMessage("<yellow>プラグイン '${outdatedInfo.pluginName}' の更新があります:</yellow>")
                    sender.sendRichMessage("  現在: ${outdatedInfo.currentVersion}")
                    sender.sendRichMessage("<green>  最新: ${outdatedInfo.latestVersion}</green>")
                    sender.sendRichMessage("<gray>更新するには 'mpm update' を実行してください。</gray>")
                } else {
                    sender.sendRichMessage(
                        "<green>プラグイン '${outdatedInfo.pluginName}' は最新です (${outdatedInfo.currentVersion})</green>"
                    )
                }
            }
        )
    }

    /**
     * すべての管理下プラグインの更新を確認するコマンド
     * @param sender コマンド送信者
     */
    @Subcommand("outdatedAll")
    suspend fun outdatedAll(sender: CommandSender) {
        sender.sendRichMessage("<gray>すべてのプラグインの更新を確認しています...</gray>")

        // PluginInfoManagerを実行
        infoManager.checkAllOutdated().fold(
            // 失敗時の処理
            { errorMessage ->
                sender.sendRichMessage("<red>$errorMessage</red>")
            },
            // 成功時の処理
            { outdatedInfoList ->
                // 更新が必要なプラグインのみフィルタリング
                val needsUpdateList = outdatedInfoList.filter { it.needsUpdate }

                if (needsUpdateList.isEmpty()) {
                    sender.sendRichMessage("<green>すべてのプラグインは最新です。</green>")
                } else {
                    sender.sendRichMessage("<yellow>以下のプラグインに更新があります:</yellow>")
                    needsUpdateList.forEach { outdatedInfo ->
                        sender.sendRichMessage(
                            "  - ${outdatedInfo.pluginName}: ${outdatedInfo.currentVersion} → ${outdatedInfo.latestVersion}"
                        )
                    }
                    sender.sendRichMessage("<gray>更新するには 'mpm update' を実行してください。</gray>")
                }
            }
        )
    }
}