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
import party.morino.mpm.api.application.model.PluginFilter
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.domain.plugin.model.ManagedPlugin
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.annotation.Switch
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * プラグインリスト表示コマンドのコントローラー
 */
@Command("mpm")
@CommandPermission("mpm.command.list")
class ListCommand : KoinComponent {
    // KoinによるDI
    private val infoService: PluginInfoService by inject()

    /**
     * 管理下のプラグインリストを表示するコマンド
     * @param sender コマンド送信者
     * @param showAll すべてのプラグインを表示するフラグ
     * @param showManaged 管理中のプラグインのみ表示するフラグ
     * @param showUnmanaged 管理されていないプラグインを表示するフラグ
     * @param showLocked ロックされているプラグインを表示するフラグ
     */
    @Subcommand("list")
    suspend fun list(
        sender: CommandSender,
        @Switch("all") showAll: Boolean = false,
        @Switch("managed") showManaged: Boolean = false,
        @Switch("unmanaged") showUnmanaged: Boolean = false,
        @Switch("locked") showLocked: Boolean = false
    ) {
        // デフォルトですべてのプラグインを表示
        val shouldShowAll = showAll || (!showManaged && !showUnmanaged && !showLocked)

        sender.sendRichMessage("===== プラグイン一覧 =====")

        if (shouldShowAll || showManaged) {
            val managedPlugins = infoService.list(PluginFilter.MANAGED)
            sender.sendRichMessage("--- 管理中のプラグイン (${managedPlugins.size}) ---")
            displayPlugins(sender, managedPlugins)
        }

        if (shouldShowAll || showUnmanaged) {
            val unmanagedPlugins = infoService.list(PluginFilter.UNMANAGED)
            sender.sendRichMessage("--- 管理されていないプラグイン (${unmanagedPlugins.size}) ---")
            displayPlugins(sender, unmanagedPlugins)
        }

        if (shouldShowAll || showLocked) {
            val lockedPlugins = infoService.list(PluginFilter.LOCKED)
            sender.sendRichMessage("--- ロック中のプラグイン (${lockedPlugins.size}) ---")
            displayPlugins(sender, lockedPlugins)
        }

        sender.sendRichMessage("=======================")
    }

    /**
     * プラグインリストを表示するヘルパーメソッド
     * @param sender コマンド送信者
     * @param plugins 表示するプラグインのリスト
     */
    private fun displayPlugins(
        sender: CommandSender,
        plugins: List<ManagedPlugin>
    ) {
        plugins.forEach { plugin ->
            val lockIcon = if (plugin.isLocked) " 🔒" else ""
            sender.sendRichMessage(" - ${plugin.name} (v${plugin.currentVersion.raw})$lockIcon")
        }

        if (plugins.isEmpty()) {
            sender.sendRichMessage(" (なし)")
        }
    }
}