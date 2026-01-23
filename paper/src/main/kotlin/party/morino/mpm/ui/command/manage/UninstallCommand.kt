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
import party.morino.mpm.api.application.plugin.PluginLifecycleService
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.model.plugin.InstalledPlugin
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * プラグインアンインストールコマンドのコントローラー
 * プレゼンテーション層とユースケース層の橋渡しを行う
 * mpm uninstall <pluginName> - プラグインをアンインストール
 */
@Command("mpm")
@CommandPermission("mpm.command")
class UninstallCommand : KoinComponent {
    // KoinによるDI
    private val lifecycleService: PluginLifecycleService by inject()

    @Subcommand("uninstall")
    @Description("指定されたプラグインをアンインストールします。")
    suspend fun uninstall(
        sender: CommandSender,
        plugin: InstalledPlugin
    ) {
        val pluginId = plugin.pluginId
        // PluginLifecycleServiceを実行
        lifecycleService.uninstall(PluginName(pluginId)).fold(
            // 失敗時の処理
            { error ->
                sender.sendRichMessage("<red>${error.message}</red>")
            },
            // 成功時の処理
            {
                sender.sendRichMessage("<green>プラグイン '$pluginId' をアンインストールしました。</green>")
                sender.sendRichMessage("<gray>変更を反映するには、サーバーを再起動してください。</gray>")
            }
        )
    }
}