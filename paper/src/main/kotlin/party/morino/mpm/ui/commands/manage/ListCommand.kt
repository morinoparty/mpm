/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.commands.manage

import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.Flag
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.core.plugin.PluginListUseCase
import party.morino.mpm.api.model.plugin.PluginData

/**
 * プラグインリスト表示コマンドのコントローラー
 */
@Command("mpm")
@Permission("mpm.command")
class ListCommand : KoinComponent {
    // KoinによるDI
    private val pluginListUseCase: PluginListUseCase by inject()

    /**
     * 管理下のプラグインリストを表示するコマンド
     * @param sender コマンド送信者
     * @param showAll すべてのプラグインを表示するフラグ
     * @param showEnabled 有効なプラグインのみ表示するフラグ
     * @param showDisabled 無効なプラグインのみ表示するフラグ
     * @param showUnmanaged 管理されていないプラグインを表示するフラグ
     */
    @Command("list")
    suspend fun list(
        sender: CommandSender,
        @Flag("all") showAll: Boolean = false,
        @Flag("enabled") showEnabled: Boolean = false,
        @Flag("disabled") showDisabled: Boolean = false,
        @Flag("unmanaged") showUnmanaged: Boolean = false
    ) {
        // デフォルトですべてのプラグインを表示
        val shouldShowAll = showAll || (!showEnabled && !showDisabled && !showUnmanaged)

        sender.sendRichMessage("===== プラグイン一覧 =====")

        if (shouldShowAll || showEnabled) {
            val enabledPlugins = pluginListUseCase.getEnabledPlugins()
            sender.sendRichMessage("--- 有効なプラグイン (${enabledPlugins.size}) ---")
            displayPlugins(sender, enabledPlugins)
        }

        if (shouldShowAll || showDisabled) {
            val disabledPlugins = pluginListUseCase.getDisabledPlugins()
            sender.sendRichMessage("--- 無効なプラグイン (${disabledPlugins.size}) ---")
            displayPlugins(sender, disabledPlugins)
        }

        if (shouldShowAll || showUnmanaged) {
            val unmanagedPlugins = pluginListUseCase.getUnmanagedPlugins()
            sender.sendRichMessage("--- 管理されていないプラグイン (${unmanagedPlugins.size}) ---")
            unmanagedPlugins.forEach { sender.sendMessage(" - $it") }
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
        plugins: List<PluginData>
    ) {
        plugins.forEach { plugin ->
            when (plugin) {
                is PluginData.BukkitPluginData -> {
                    sender.sendRichMessage(" - ${plugin.name} (v${plugin.version})")
                }
                is PluginData.PaperPluginData -> {
                    sender.sendRichMessage(" - ${plugin.name} (v${plugin.version})")
                }
            }
        }

        if (plugins.isEmpty()) {
            sender.sendRichMessage(" (なし)")
        }
    }
}