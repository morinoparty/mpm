/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.command.repo

import org.bukkit.command.CommandSender
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.core.repository.RepositoryManager
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission
import sun.jvmstat.perfdata.monitor.protocol.local.PerfDataFile.fileNamePattern

/**
 * リポジトリファイルを作成するコマンドのコントローラー
 * URLからリポジトリタイプを判定し、repository/{pluginName}.jsonを生成する
 * mpm create-repo <pluginName> <url> [fileNamePattern]
 */
@Command("mpm")
@CommandPermission("mpm.command")
class RepositoryCommands : KoinComponent {
    private val repositorySourceManager: RepositoryManager by inject()

    /**
     * 利用可能なリポジトリファイルの一覧を表示するコマンド
     * @param sender コマンド送信者
     */
    @Subcommand("repository list")
    suspend fun listRepositories(sender: CommandSender) {
        // すべてのリポジトリソースから利用可能なプラグインを収集
        val allPlugins = repositorySourceManager.getAvailablePlugins().toMutableSet()
        val sourceInfo = mutableListOf<Pair<String, List<String>>>()

        for (source in repositorySourceManager.getRepositorySources()) {
            try {
                if (source.isAvailable()) {
                    val plugins = source.getAvailablePlugins()
                    sourceInfo.add(
                        source.getIdentifier() to plugins
                    )
                }
            } catch (e: Exception) {
                // エラーが発生した場合はスキップ
                continue
            }
        }

        // 結果を表示
        if (allPlugins.isEmpty()) {
            sender.sendRichMessage("<yellow>利用可能なリポジトリファイルが見つかりませんでした。")
            sender.sendRichMessage("<gray>'mpm create-repo' コマンドでリポジトリファイルを作成できます。")
            return
        }

        // ヘッダー
        sender.sendRichMessage("<green>利用可能なリポジトリ一覧 (合計: ${allPlugins.size})")

        // ソースごとに表示
        for ((sourceType, plugins) in sourceInfo) {
            if (plugins.isNotEmpty()) {
                sender.sendRichMessage("<white>[$sourceType] ${plugins.size}個")
                plugins.sorted().forEach { pluginName ->
                    sender.sendRichMessage("<white>  - $pluginName")
                }
            }
        }

        // フッター
        sender.sendRichMessage("<gray>プラグインを追加するには: /mpm add <pluginName>")
    }
}