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
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.annotation.Switch
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * プラグイン追加コマンドのコントローラー
 * プレゼンテーション層とユースケース層の橋渡しを行う
 * mpm add <pluginName> - プラグインを管理対象に追加（依存関係も含む）
 */
@Command("mpm")
@CommandPermission("mpm.command")
class AddCommand : KoinComponent {
    // KoinによるDI
    private val lifecycleService: PluginLifecycleService by inject()

    /**
     * プラグインを管理対象に追加するコマンド（バージョン指定なし）
     * @param sender コマンド送信者
     * @param plugin プラグイン名
     * @param noDeps 依存関係を追加しない場合はtrue
     * @param soft softDependenciesも含める場合はtrue
     */
    @Subcommand("add")
    suspend fun add(
        sender: CommandSender,
        plugin: RepositoryPlugin,
        @Switch("no-deps") noDeps: Boolean = false,
        @Switch("soft") soft: Boolean = false
    ) {
        addWithVersion(sender, plugin, VersionSpecifier.Latest, noDeps, soft)
    }

    /**
     * プラグインを管理対象に追加するコマンド（バージョン指定あり）
     * @param sender コマンド送信者
     * @param plugin プラグイン名
     * @param version バージョン指定
     * @param noDeps 依存関係を追加しない場合はtrue
     * @param soft softDependenciesも含める場合はtrue
     */
    @Subcommand("add")
    suspend fun addWithVersion(
        sender: CommandSender,
        plugin: RepositoryPlugin,
        version: VersionSpecifier,
        @Switch("no-deps") noDeps: Boolean = false,
        @Switch("soft") soft: Boolean = false
    ) {
        val pluginId = plugin.pluginId

        if (noDeps) {
            // 依存関係なしで追加（従来の動作）
            addSinglePlugin(sender, pluginId, version)
        } else {
            // 依存関係を含めて追加
            addWithDependencies(sender, pluginId, version, soft)
        }
    }

    /**
     * 単一のプラグインを追加（依存関係なし）
     */
    private suspend fun addSinglePlugin(
        sender: CommandSender,
        pluginId: String,
        version: VersionSpecifier
    ) {
        sender.sendRichMessage("<gray>プラグイン '$pluginId' の情報を取得しています...")

        lifecycleService.add(PluginName(pluginId), version).fold(
            { error ->
                sender.sendRichMessage("<red>${error.message}")
            },
            {
                sender.sendRichMessage("<green>プラグイン '$pluginId' の情報を追加しました。")
                sender.sendRichMessage("<gray>プラグイン '$pluginId' をインストールしています...")

                lifecycleService.install(PluginName(pluginId)).fold(
                    { error ->
                        sender.sendRichMessage("<red>${error.message}")
                    },
                    { installResult ->
                        displayInstallResult(sender, installResult)
                        sender.sendRichMessage("<gray>変更を反映するには、サーバーを再起動してください。")
                    }
                )
            }
        )
    }

    /**
     * 依存関係を含めてプラグインを追加
     */
    private suspend fun addWithDependencies(
        sender: CommandSender,
        pluginId: String,
        version: VersionSpecifier,
        includeSoftDependencies: Boolean
    ) {
        sender.sendRichMessage("<gray>プラグイン '$pluginId' と依存関係を解決しています...")

        lifecycleService.addWithDependencies(
            PluginName(pluginId),
            version,
            includeSoftDependencies
        ).fold(
            { error ->
                sender.sendRichMessage("<red>${error.message}")
            },
            { result ->
                // 追加されたプラグインを表示
                if (result.addedPlugins.isNotEmpty()) {
                    sender.sendRichMessage("<green>===== 追加されたプラグイン (${result.addedPlugins.size}) =====")
                    result.addedPlugins.forEach { addResult ->
                        val depMark = if (addResult.isDependency) "<gray>[依存]</gray> " else ""
                        displayInstallResult(sender, addResult.installResult, depMark)
                    }
                }

                // スキップされたプラグインを表示
                if (result.skippedPlugins.isNotEmpty()) {
                    sender.sendRichMessage("<yellow>===== スキップ (既に追加済み: ${result.skippedPlugins.size}) =====")
                    result.skippedPlugins.forEach { name ->
                        sender.sendRichMessage("<yellow>~<reset> $name")
                    }
                }

                // 見つからなかった依存プラグインを表示
                if (result.notFoundPlugins.isNotEmpty()) {
                    sender.sendRichMessage("<red>===== リポジトリに見つからない依存関係 (${result.notFoundPlugins.size}) =====")
                    result.notFoundPlugins.forEach { name ->
                        sender.sendRichMessage("<red>!<reset> $name <gray>(手動でインストールが必要)")
                    }
                }

                // 失敗したプラグインを表示
                if (result.failedPlugins.isNotEmpty()) {
                    sender.sendRichMessage("<red>===== 追加失敗 (${result.failedPlugins.size}) =====")
                    result.failedPlugins.forEach { (name, error) ->
                        sender.sendRichMessage("<red>✗<reset> $name: $error")
                    }
                }

                // サマリー
                sender.sendRichMessage("<gray>-----")
                sender.sendRichMessage(
                    "<gray>合計: " +
                        "<green>${result.addedPlugins.size}個追加</green>, " +
                        "<yellow>${result.skippedPlugins.size}個スキップ</yellow>, " +
                        "<red>${result.failedPlugins.size + result.notFoundPlugins.size}個失敗</red>"
                )

                if (result.addedPlugins.isNotEmpty()) {
                    sender.sendRichMessage("<gray>変更を反映するには、サーバーを再起動してください。")
                }
            }
        )
    }

    /**
     * インストール結果を表示するヘルパーメソッド
     */
    private fun displayInstallResult(
        sender: CommandSender,
        installResult: party.morino.mpm.api.application.model.InstallResult,
        prefix: String = ""
    ) {
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
        sender.sendRichMessage("$prefix<green>+<reset> ${installResult.installed.name} <gray>$versionInfo")
    }
}