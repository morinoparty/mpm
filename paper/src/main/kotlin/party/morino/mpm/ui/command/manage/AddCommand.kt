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
import party.morino.mpm.api.core.plugin.PluginLifecycleManager
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * プラグイン追加コマンドのコントローラー
 * プレゼンテーション層とユースケース層の橋渡しを行う
 * mpm add <pluginName> - プラグインを管理対象に追加
 */
@Command("mpm")
@CommandPermission("mpm.command")
class AddCommand : KoinComponent {
    // KoinによるDI
    private val lifecycleManager: PluginLifecycleManager by inject()

    /**
     * プラグインを管理対象に追加するコマンド
     * @param sender コマンド送信者
     * @param plugin プラグイン名
     */
    @Subcommand("add")
    suspend fun add(
        sender: CommandSender,
        plugin: RepositoryPlugin
    ) {
        val pluginName = plugin.pluginId
        sender.sendRichMessage("<gray>プラグイン '$pluginName' の情報を取得しています...")

        // PluginLifecycleManagerを実行
        lifecycleManager.add(plugin).fold(
            // 失敗時の処理
            { errorMessage ->
                sender.sendRichMessage("<red>$errorMessage")
            },
            // 成功時の処理
            {
                sender.sendRichMessage("<green>プラグイン '$pluginName' の情報を追加しました。")
                sender.sendRichMessage("<gray>プラグイン '$pluginName' をインストールしています...")

                lifecycleManager.install(plugin).fold(
                    { errorMessage ->
                        sender.sendRichMessage("<red>$errorMessage")
                    },
                    { installResult ->
                        // 削除されたプラグイン情報を表示
                        installResult.removed?.let { removedInfo ->
                            sender.sendRichMessage("<red>-<reset> ${removedInfo.name} <gray>${removedInfo.version}")
                        }

                        // インストールされたプラグイン情報を表示
                        val versionInfo =
                            if (installResult.installed.latestVersion.isEmpty() ||
                                installResult.installed.currentVersion == installResult.installed.latestVersion
                            ) {
                                installResult.installed.currentVersion
                            } else {
                                "${installResult.installed.currentVersion} (${installResult.installed.latestVersion}が有効)"
                            }
                        sender.sendRichMessage("<green>+<reset> ${installResult.installed.name} <gray>$versionInfo")

                        sender.sendRichMessage("<gray>変更を反映するには、サーバーを再起動してください。")
                    }
                )
            }
        )
    }
}