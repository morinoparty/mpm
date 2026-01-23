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
import party.morino.mpm.api.domain.dependency.DependencyAnalyzer
import party.morino.mpm.api.model.dependency.DependencyNode
import party.morino.mpm.api.model.plugin.InstalledPlugin
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Optional
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.annotation.Switch
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * 依存関係コマンドのコントローラー
 * プラグインの依存関係を解析・表示する機能を提供
 */
@Command("mpm", "mpm deps")
@CommandPermission("mpm.command")
class DependencyCommand : KoinComponent {
    // KoinによるDI
    private val dependencyAnalyzer: DependencyAnalyzer by inject()

    /**
     * 依存関係ツリーを表示するコマンド
     * @param sender コマンド送信者
     * @param plugin 対象プラグイン
     * @param soft softDependも表示するかどうか
     */
    @Subcommand("deps tree")
    fun tree(
        sender: CommandSender,
        plugin: InstalledPlugin,
        @Switch("soft") soft: Boolean = false
    ) {
        val pluginName = plugin.pluginId

        dependencyAnalyzer.buildDependencyTree(pluginName, soft).fold(
            // 失敗時の処理
            { error ->
                sender.sendRichMessage("<red>$error")
            },
            // 成功時の処理
            { tree ->
                sender.sendRichMessage("<yellow>依存関係ツリー: <white>$pluginName")
                renderTree(sender, tree.root, "", true)

                // 不足している依存を表示
                if (tree.missingRequired.isNotEmpty()) {
                    sender.sendRichMessage("")
                    sender.sendRichMessage("<red>不足している必須依存:")
                    for (missing in tree.missingRequired) {
                        sender.sendRichMessage("<red>  - $missing")
                    }
                }

                if (soft && tree.missingSoft.isNotEmpty()) {
                    sender.sendRichMessage("")
                    sender.sendRichMessage("<yellow>不足しているオプション依存:")
                    for (missing in tree.missingSoft) {
                        sender.sendRichMessage("<yellow>  - $missing")
                    }
                }
            }
        )
    }

    /**
     * 不足している依存関係をチェックするコマンド
     * @param sender コマンド送信者
     * @param plugin 対象プラグイン（省略可能、省略時は全プラグイン）
     */
    @Subcommand("deps check")
    fun check(
        sender: CommandSender,
        @Optional plugin: InstalledPlugin? = null
    ) {
        val pluginName = plugin?.pluginId

        val missingDeps = dependencyAnalyzer.checkMissingDependencies(pluginName)

        if (missingDeps.isEmpty()) {
            if (pluginName != null) {
                sender.sendRichMessage("<green>$pluginName の依存関係は全て満たされています。")
            } else {
                sender.sendRichMessage("<green>全プラグインの依存関係が満たされています。")
            }
            return
        }

        sender.sendRichMessage("<yellow>不足している依存関係:")
        for ((plugin, deps) in missingDeps) {
            sender.sendRichMessage("<red>$plugin:")
            for (dep in deps) {
                sender.sendRichMessage("<red>  - $dep")
            }
        }
    }

    /**
     * 逆依存関係を表示するコマンド
     * @param sender コマンド送信者
     * @param plugin 対象プラグイン
     */
    @Subcommand("deps reverse")
    fun reverse(
        sender: CommandSender,
        plugin: InstalledPlugin
    ) {
        val pluginName = plugin.pluginId
        val dependents = dependencyAnalyzer.getReverseDependencies(pluginName)

        if (dependents.isEmpty()) {
            sender.sendRichMessage("<gray>$pluginName に依存しているプラグインはありません。")
            return
        }

        sender.sendRichMessage("<yellow>$pluginName に依存しているプラグイン:")
        for (dependent in dependents) {
            sender.sendRichMessage("<white>  - $dependent")
        }
    }

    /**
     * プラグインの依存関係情報を表示するコマンド
     * @param sender コマンド送信者
     * @param plugin 対象プラグイン
     */
    @Subcommand("deps info")
    fun info(
        sender: CommandSender,
        plugin: InstalledPlugin
    ) {
        val pluginName = plugin.pluginId

        dependencyAnalyzer.getDependencyInfo(pluginName).fold(
            // 失敗時の処理
            { error ->
                sender.sendRichMessage("<red>$error")
            },
            // 成功時の処理
            { info ->
                sender.sendRichMessage("<yellow>依存関係情報: <white>$pluginName")

                if (info.depend.isNotEmpty()) {
                    sender.sendRichMessage("<gray>必須依存 (depend):")
                    for (dep in info.depend) {
                        sender.sendRichMessage("<white>  - $dep")
                    }
                } else {
                    sender.sendRichMessage("<gray>必須依存: なし")
                }

                if (info.softDepend.isNotEmpty()) {
                    sender.sendRichMessage("<gray>オプション依存 (softdepend):")
                    for (dep in info.softDepend) {
                        sender.sendRichMessage("<white>  - $dep")
                    }
                } else {
                    sender.sendRichMessage("<gray>オプション依存: なし")
                }

                if (info.loadBefore.isNotEmpty()) {
                    sender.sendRichMessage("<gray>先読み (loadbefore):")
                    for (dep in info.loadBefore) {
                        sender.sendRichMessage("<white>  - $dep")
                    }
                }
            }
        )
    }

    /**
     * 依存関係ツリーをテキストとしてレンダリングする
     * @param sender コマンド送信者
     * @param node ノード
     * @param prefix プレフィックス
     * @param isLast 最後のノードかどうか
     */
    private fun renderTree(
        sender: CommandSender,
        node: DependencyNode,
        prefix: String,
        isLast: Boolean
    ) {
        // ノードの表示
        val connector = if (isLast) "└── " else "├── "
        val statusColor = if (node.isInstalled) "<green>" else "<red>"
        val requiredMark = if (node.isRequired) "" else " <gray>(optional)"

        sender.sendRichMessage("$prefix$connector$statusColor${node.pluginName}$requiredMark")

        // 子ノードの表示
        val childPrefix = prefix + if (isLast) "    " else "│   "
        for ((index, child) in node.children.withIndex()) {
            renderTree(sender, child, childPrefix, index == node.children.size - 1)
        }
    }
}