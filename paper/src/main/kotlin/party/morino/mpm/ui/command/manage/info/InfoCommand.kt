/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.command.manage.info

import org.bukkit.command.CommandSender
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import party.morino.mpm.utils.escapeMiniMessage
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * プラグイン詳細表示コマンドのコントローラー
 *
 * mpm info <plugin> - プラグインの説明・ライセンス・DL数・最新バージョン等を表示する
 */
@Command("mpm")
@CommandPermission("mpm.command.list")
class InfoCommand : KoinComponent {
    // Koinによる依存性注入
    private val infoService: PluginInfoService by inject()

    /**
     * 指定プラグインの詳細情報を表示するコマンド
     *
     * @param sender コマンド送信者
     * @param plugin リポジトリプラグイン
     */
    @Subcommand("info")
    @Description("プラグインの詳細情報を表示します。")
    suspend fun info(
        sender: CommandSender,
        plugin: RepositoryPlugin
    ) {
        val pluginId = plugin.pluginId
        sender.sendRichMessage("<gray>'$pluginId' の情報を取得しています...</gray>")

        infoService.getPluginDetail(PluginName(pluginId)).fold(
            // 失敗時の処理
            { error ->
                sender.sendRichMessage("<red>${error.message}</red>")
            },
            // 成功時の処理
            { detail ->
                // 名前・説明・ライセンス・ホームページ等はリポジトリ由来の信頼できない文字列のため、
                // MiniMessageタグとして解釈されないようエスケープして表示する
                val project = detail.project
                sender.sendRichMessage("<green>=== ${project.name.escapeMiniMessage()} ===</green>")
                sender.sendRichMessage(
                    "<gray>ソース:</gray> ${project.source.name} <gray>(${project.slug.escapeMiniMessage()})</gray>"
                )

                // インストール状態を表示
                val stateText =
                    when {
                        detail.installedVersion != null && detail.locked ->
                            "<aqua>インストール済み: ${detail.installedVersion}</aqua> <gold>🔒ロック中</gold>"
                        detail.installedVersion != null ->
                            "<aqua>インストール済み: ${detail.installedVersion}</aqua>"
                        else -> "<gray>未インストール</gray>"
                    }
                sender.sendRichMessage("<gray>状態:</gray> $stateText")

                // 各フィールドを表示（取得できたもののみ）。数値以外はエスケープする
                project.latestVersion?.let {
                    sender.sendRichMessage("<gray>最新バージョン:</gray> ${it.escapeMiniMessage()}")
                }
                project.downloads?.let { sender.sendRichMessage("<gray>ダウンロード数:</gray> $it") }
                project.license?.let { sender.sendRichMessage("<gray>ライセンス:</gray> ${it.escapeMiniMessage()}") }
                project.homepage?.let {
                    sender.sendRichMessage("<gray>ホームページ:</gray> <blue>${it.escapeMiniMessage()}</blue>")
                }
                project.description
                    ?.takeIf { it.isNotBlank() }
                    ?.let { sender.sendRichMessage("<gray>説明:</gray> ${it.escapeMiniMessage()}") }
            }
        )
    }
}