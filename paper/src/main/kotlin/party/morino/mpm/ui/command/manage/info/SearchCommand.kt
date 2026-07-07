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
import party.morino.mpm.api.application.search.PluginSearchService
import party.morino.mpm.utils.escapeMiniMessage
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Flag
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * プラグイン検索コマンドのコントローラー
 *
 * mpm search <query> [--limit <数>] - 複数リポジトリを横断してプラグインを検索する
 */
@Command("mpm")
@CommandPermission("mpm.command.list")
class SearchCommand : KoinComponent {
    // Koinによる依存性注入
    private val searchService: PluginSearchService by inject()

    /**
     * キーワードでプラグインを検索するコマンド
     *
     * @param sender コマンド送信者
     * @param query 検索キーワード
     * @param limit 表示件数の上限（デフォルト: 10）
     */
    @Subcommand("search")
    @Description("複数リポジトリを横断してプラグインを検索します。")
    suspend fun search(
        sender: CommandSender,
        query: String,
        @Flag("limit") limit: Int = 10
    ) {
        // --limitに0以下を渡すとtake()が例外を投げるため事前に検証する
        if (limit <= 0) {
            sender.sendRichMessage("<red>--limitには1以上の値を指定してください。</red>")
            return
        }

        sender.sendRichMessage("<gray>'$query' を検索しています...</gray>")

        searchService.search(query, limit).fold(
            // 失敗時の処理
            { error ->
                sender.sendRichMessage("<red>${error.message}</red>")
            },
            // 成功時の処理
            { results ->
                if (results.isEmpty()) {
                    sender.sendRichMessage("<yellow>'$query' に一致するプラグインが見つかりませんでした。</yellow>")
                    return@fold
                }

                // クエリはユーザー入力なのでエスケープして表示する
                sender.sendRichMessage(
                    "<green>=== 検索結果 (${query.escapeMiniMessage()}) — ${results.size}件 ===</green>"
                )
                results.forEach { result ->
                    // 名前・説明・id・URLはリポジトリ由来の信頼できない文字列のため、
                    // MiniMessageタグとして解釈されないようエスケープする
                    val downloadsText = result.downloads?.let { " <gray>(DL: $it)</gray>" } ?: ""
                    sender.sendRichMessage(
                        "<aqua>[${result.source.name}]</aqua> " +
                            "<white>${result.name.escapeMiniMessage()}</white>$downloadsText"
                    )
                    // 識別子（リポジトリファイル作成の参考用）と概要を表示
                    sender.sendRichMessage("  <gray>id: ${result.slug.escapeMiniMessage()}</gray>")
                    result.description
                        ?.takeIf { it.isNotBlank() }
                        ?.let { sender.sendRichMessage("  <gray>${it.take(100).escapeMiniMessage()}</gray>") }
                    // プロジェクトページのURLを表示（詳細確認・リポジトリファイル作成の参考用）
                    result.url?.let { sender.sendRichMessage("  <blue>${it.escapeMiniMessage()}</blue>") }
                }
                sender.sendRichMessage(
                    "<gray>インストールするには、対象プラグインのリポジトリファイルを用意して 'mpm add' を実行してください。</gray>"
                )
            }
        )
    }
}