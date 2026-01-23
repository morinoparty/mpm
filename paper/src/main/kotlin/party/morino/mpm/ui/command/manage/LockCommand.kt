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
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.model.plugin.InstalledPlugin
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * プラグインロック/アンロックコマンドのコントローラー
 * プレゼンテーション層とユースケース層の橋渡しを行う
 * mpm lock <plugin> - プラグインをロックして自動更新を防ぐ
 * mpm unlock <plugin> - プラグインのロックを解除
 */
@Command("mpm")
@CommandPermission("mpm.command")
class LockCommand : KoinComponent {
    // Koinによる依存性注入
    private val updateService: PluginUpdateService by inject()

    /**
     * プラグインをロックするコマンド
     * @param sender コマンド送信者
     * @param plugin インストール済みプラグイン
     */
    @Subcommand("lock")
    suspend fun lock(
        sender: CommandSender,
        plugin: InstalledPlugin
    ) {
        val pluginId = plugin.pluginId
        // PluginUpdateServiceを実行
        updateService.lock(PluginName(pluginId)).fold(
            // 失敗時の処理
            { error ->
                sender.sendRichMessage("<red>${error.message}</red>")
            },
            // 成功時の処理
            {
                sender.sendRichMessage("<green>プラグイン '$pluginId' をロックしました。</green>")
                sender.sendRichMessage("<gray>このプラグインは自動更新されません。</gray>")
            }
        )
    }

    /**
     * プラグインのロックを解除するコマンド
     * @param sender コマンド送信者
     * @param plugin インストール済みプラグイン
     */
    @Subcommand("unlock")
    suspend fun unlock(
        sender: CommandSender,
        plugin: InstalledPlugin
    ) {
        val pluginId = plugin.pluginId
        // PluginUpdateServiceを実行
        updateService.unlock(PluginName(pluginId)).fold(
            // 失敗時の処理
            { error ->
                sender.sendRichMessage("<red>${error.message}</red>")
            },
            // 成功時の処理
            {
                sender.sendRichMessage("<green>プラグイン '$pluginId' のロックを解除しました。</green>")
                sender.sendRichMessage("<gray>このプラグインは自動更新の対象になります。</gray>")
            }
        )
    }
}