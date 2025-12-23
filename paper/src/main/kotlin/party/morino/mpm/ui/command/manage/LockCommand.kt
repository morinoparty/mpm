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
import party.morino.mpm.api.core.plugin.PluginUpdateManager
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
    private val updateManager: PluginUpdateManager by inject()

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
        val pluginName = plugin.pluginId
        // PluginUpdateManagerを実行
        updateManager.lock(plugin).fold(
            // 失敗時の処理
            { errorMessage ->
                sender.sendRichMessage("<red>$errorMessage</red>")
            },
            // 成功時の処理
            {
                sender.sendRichMessage("<green>プラグイン '$pluginName' をロックしました。</green>")
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
        val pluginName = plugin.pluginId
        // PluginUpdateManagerを実行
        updateManager.unlock(plugin).fold(
            // 失敗時の処理
            { errorMessage ->
                sender.sendRichMessage("<red>$errorMessage</red>")
            },
            // 成功時の処理
            {
                sender.sendRichMessage("<green>プラグイン '$pluginName' のロックを解除しました。</green>")
                sender.sendRichMessage("<gray>このプラグインは自動更新の対象になります。</gray>")
            }
        )
    }
}