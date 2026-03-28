/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
 * and related and neighboring rights to this software to the public domain worldwide.
 * This software is distributed without any warranty.
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
import party.morino.mpm.api.application.plugin.PluginLifecycleService
import party.morino.mpm.api.domain.repository.RepositoryManager
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.annotation.Switch
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * unmanagedプラグインのadoptコマンド
 *
 * すべてのunmanagedプラグインをリポジトリから検索し、
 * 見つかった場合はmanaged状態に変更してダウンロードする
 *
 * mpm adopt           - すべてのunmanagedプラグインをadopt
 * mpm adopt --dry-run - 対象プラグインを表示のみ（実行しない）
 * mpm adopt --soft    - softDependenciesも含める
 */
@Command("mpm")
@CommandPermission("mpm.command.add")
class AdoptCommand : KoinComponent {
    // KoinによるDI
    private val lifecycleService: PluginLifecycleService by inject()
    private val infoService: PluginInfoService by inject()
    private val repositoryManager: RepositoryManager by inject()

    /**
     * すべてのunmanagedプラグインをリポジトリから検索してadoptする
     *
     * @param sender コマンド送信者
     * @param soft softDependenciesも含める場合はtrue
     * @param dryRun 実行せず対象プラグインを表示のみの場合はtrue
     */
    @Subcommand("adopt")
    suspend fun adopt(
        sender: CommandSender,
        @Switch("soft") soft: Boolean = false,
        @Switch("dry-run") dryRun: Boolean = false
    ) {
        if (dryRun) {
            // dry-runモード: 対象プラグインを表示のみ
            executeDryRun(sender)
        } else {
            // 通常実行: adoptを実行
            executeAdopt(sender, soft)
        }
    }

    /**
     * dry-runモード: 対象プラグインを表示のみ
     */
    private suspend fun executeDryRun(sender: CommandSender) {
        sender.sendRichMessage("<gray>unmanagedプラグインをリポジトリから検索しています...")

        // unmanagedプラグイン一覧を取得
        val unmanagedPlugins = infoService.list(PluginFilter.UNMANAGED)
        val unmanagedNames = unmanagedPlugins.map { it.name.value }

        if (unmanagedNames.isEmpty()) {
            sender.sendRichMessage("<yellow>unmanagedプラグインはありません。")
            return
        }

        // リポジトリの利用可能なプラグイン一覧を取得
        val availablePlugins = repositoryManager.getAvailablePlugins()
        val availablePluginsLower = availablePlugins.map { it.lowercase() }.toSet()

        // マッチングを行う
        val matchedPlugins = mutableListOf<String>()
        val skippedPlugins = mutableListOf<String>()

        for (name in unmanagedNames) {
            if (availablePluginsLower.contains(name.lowercase())) {
                matchedPlugins.add(name)
            } else {
                skippedPlugins.add(name)
            }
        }

        // 結果を表示
        if (matchedPlugins.isNotEmpty()) {
            sender.sendRichMessage("<green>===== adoptされるプラグイン (${matchedPlugins.size}) =====")
            matchedPlugins.forEach { name ->
                sender.sendRichMessage("<green>-<reset> $name")
            }
        }

        if (skippedPlugins.isNotEmpty()) {
            sender.sendRichMessage("<yellow>===== リポジトリに見つからない (${skippedPlugins.size}) =====")
            skippedPlugins.forEach { name ->
                sender.sendRichMessage("<yellow>~<reset> $name")
            }
        }

        sender.sendRichMessage("<gray>-----")
        sender.sendRichMessage("<gray>実行するには --dry-run を外してください")
    }

    /**
     * 通常実行: adoptを実行
     */
    private suspend fun executeAdopt(
        sender: CommandSender,
        includeSoftDependencies: Boolean
    ) {
        sender.sendRichMessage("<gray>unmanagedプラグインをリポジトリから検索しています...")

        lifecycleService.adoptAll(includeSoftDependencies).fold(
            { error ->
                sender.sendRichMessage("<red>${error.message}")
            },
            { result ->
                // adoptされたプラグインを表示
                if (result.adoptedPlugins.isNotEmpty()) {
                    sender.sendRichMessage("<green>===== adoptされたプラグイン (${result.adoptedPlugins.size}) =====")
                    result.adoptedPlugins.forEach { addResult ->
                        val depMark = if (addResult.isDependency) "<gray>[依存]</gray> " else ""
                        displayInstallResult(sender, addResult.installResult, depMark)
                    }
                }

                // リポジトリに見つからなかったプラグインを表示
                if (result.skippedPlugins.isNotEmpty()) {
                    sender.sendRichMessage("<yellow>===== リポジトリに見つからない (${result.skippedPlugins.size}) =====")
                    result.skippedPlugins.forEach { name ->
                        sender.sendRichMessage("<yellow>~<reset> $name <gray>(手動管理のまま)")
                    }
                }

                // 見つからなかった依存プラグインを表示
                if (result.notFoundDependencies.isNotEmpty()) {
                    sender.sendRichMessage("<red>===== 依存関係が見つからない (${result.notFoundDependencies.size}) =====")
                    result.notFoundDependencies.forEach { name ->
                        sender.sendRichMessage("<red>!<reset> $name <gray>(手動でインストールが必要)")
                    }
                }

                // 失敗したプラグインを表示
                if (result.failedPlugins.isNotEmpty()) {
                    sender.sendRichMessage("<red>===== adopt失敗 (${result.failedPlugins.size}) =====")
                    result.failedPlugins.forEach { (name, error) ->
                        sender.sendRichMessage("<red>✗<reset> $name: $error")
                    }
                }

                // サマリー
                sender.sendRichMessage("<gray>-----")
                sender.sendRichMessage(
                    "<gray>合計: " +
                        "<green>${result.adoptedPlugins.size}個adopt</green>, " +
                        "<yellow>${result.skippedPlugins.size}個スキップ</yellow>, " +
                        "<red>${result.failedPlugins.size}個失敗</red>"
                )

                if (result.adoptedPlugins.isNotEmpty()) {
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
