/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.command.repo

import net.kyori.adventure.audience.Audience
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.Logger
import party.morino.mpm.api.domain.downloader.model.TargetPlatform
import party.morino.mpm.api.domain.repository.RepositoryManager
import party.morino.mpm.ui.command.annotation.MpmPermission
import party.morino.mpm.utils.command.sendRichMessage
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand

/**
 * リポジトリ管理コマンドのコントローラー
 * リポジトリの一覧表示を担当する
 */
@Command("mpm")
@MpmPermission("mpm.command")
class RepositoryCommands : KoinComponent {
    private val repositorySourceManager: RepositoryManager by inject()

    // 補完や操作の失敗を管理者が追えるよう、プラットフォームのロガーへ出力する
    private val logger: Logger by inject()

    // 案内メッセージに出すコマンドのルート名（Paper は mpm、Velocity は mpmv）
    private val targetPlatform: TargetPlatform by inject()

    /**
     * 利用可能なリポジトリファイルの一覧を表示するコマンド
     * @param sender コマンド送信者
     */
    @Subcommand("repository list")
    suspend fun listRepositories(sender: Audience) {
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
                // エラーが発生した場合はスキップ（管理者が原因を追えるようログに残す）
                logger.warn("リポジトリ '${source.getIdentifier()}' の取得に失敗: ${e.message}")
                continue
            }
        }

        // 結果を表示
        if (allPlugins.isEmpty()) {
            sender.sendRichMessage("<yellow>利用可能なリポジトリファイルが見つかりませんでした。")
            sender.sendRichMessage("<gray>config.jsonにリポジトリを追加した後、'/${targetPlatform.commandRoot} reload'で反映してください。")
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
        sender.sendRichMessage("<gray>プラグインを追加するには: /${targetPlatform.commandRoot} add <pluginName>")
    }
}