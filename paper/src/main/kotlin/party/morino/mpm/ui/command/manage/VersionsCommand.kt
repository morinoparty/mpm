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
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Flag
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * プラグインバージョン一覧表示コマンドのコントローラー
 * プレゼンテーション層とユースケース層の橋渡しを行う
 * mpm versions <plugin> [--limit <数>] - プラグインの利用可能なバージョン一覧を表示
 */
@Command("mpm")
@CommandPermission("mpm.command")
class VersionsCommand : KoinComponent {
    // Koinによる依存性注入
    private val infoService: PluginInfoService by inject()

    /**
     * 指定されたプラグインの利用可能なバージョン一覧を表示するコマンド
     * @param sender コマンド送信者
     * @param plugin リポジトリプラグイン
     * @param limit 表示するバージョンの最大数（デフォルト: 20）
     */
    @Subcommand("versions")
    suspend fun versions(
        sender: CommandSender,
        plugin: RepositoryPlugin,
        @Flag("limit") limit: Int = 20
    ) {
        val pluginId = plugin.pluginId
        sender.sendRichMessage("<gray>プラグイン '$pluginId' のバージョン一覧を取得しています...</gray>")

        // PluginInfoServiceを実行
        infoService.getVersions(PluginName(pluginId)).fold(
            // 失敗時の処理
            { error ->
                sender.sendRichMessage("<red>${error.message}</red>")
            },
            // 成功時の処理
            { versions ->
                if (versions.isEmpty()) {
                    sender.sendRichMessage("<yellow>プラグイン '$plugin' のバージョンが見つかりませんでした。")
                } else {
                    // 表示件数を制限
                    val displayVersions = versions.take(limit)
                    val totalCount = versions.size
                    val displayCount = displayVersions.size

                    // ヘッダー表示
                    val headerMessage =
                        if (totalCount > limit) {
                            "=== プラグイン '$plugin' のバージョン一覧 ($displayCount/${totalCount}件を表示) ==="
                        } else {
                            "=== プラグイン '$plugin' のバージョン一覧 (${totalCount}件) ==="
                        }
                    sender.sendRichMessage("<green>$headerMessage</green>")

                    // バージョン一覧を表示
                    displayVersions.forEachIndexed { index, version ->
                        // 最新版を強調表示
                        if (index == 0) {
                            sender.sendRichMessage("<aqua> - $version (最新)</aqua>")
                        } else {
                            sender.sendRichMessage(" - $version")
                        }
                    }

                    // フッター表示
                    if (totalCount > limit) {
                        sender.sendRichMessage("<gray>残り${totalCount - displayCount} 件のバージョンがあります。</gray>")
                        sender.sendRichMessage("<gray>すべて表示するには --limit $totalCount を指定してください。")
                    }
                    sender.sendRichMessage("<green>${"=".repeat(50)}</green>")
                }
            }
        )
    }
}