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
import party.morino.mpm.api.application.model.verify.VerifyStatus
import party.morino.mpm.api.application.plugin.PluginInfoService
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * プラグイン整合性検証コマンドのコントローラー
 *
 * mpm verify - インストール済み管理下プラグインのJARを、メタデータに保存された
 * sha256と照合して、破損・改竄がないかを確認する。
 */
@Command("mpm")
@CommandPermission("mpm.command.list")
class VerifyCommand : KoinComponent {
    // Koinによる依存性注入
    private val infoService: PluginInfoService by inject()

    /**
     * すべての管理下プラグインの整合性を検証するコマンド
     *
     * @param sender コマンド送信者
     */
    @Subcommand("verify")
    @Description("インストール済みプラグインの整合性を検証します。")
    suspend fun verify(sender: CommandSender) {
        sender.sendRichMessage("<gray>インストール済みプラグインの整合性を検証しています...</gray>")

        infoService.verifyInstalled().fold(
            // 失敗時の処理
            { error ->
                sender.sendRichMessage("<red>${error.message}</red>")
            },
            // 成功時の処理
            { entries ->
                if (entries.isEmpty()) {
                    sender.sendRichMessage("<gray>検証対象の管理下プラグインがありません。</gray>")
                    return@fold
                }

                // 状態ごとに集計して表示する
                val mismatched = entries.filter { it.status == VerifyStatus.MISMATCH }
                val missing = entries.filter { it.status == VerifyStatus.FILE_MISSING }
                val noHash = entries.filter { it.status == VerifyStatus.NO_HASH }
                val ok = entries.filter { it.status == VerifyStatus.OK }

                // 検証OK
                ok.forEach { entry ->
                    sender.sendRichMessage("<green>✓<reset> ${entry.pluginName} <gray>(検証OK)</gray>")
                }

                // ハッシュ不一致（破損・改竄の可能性）
                mismatched.forEach { entry ->
                    sender.sendRichMessage("<red>✗<reset> ${entry.pluginName} <red>(ハッシュ不一致)</red>")
                    sender.sendRichMessage("<gray>    expected: ${entry.expectedSha256}</gray>")
                    sender.sendRichMessage("<gray>    actual:   ${entry.actualSha256}</gray>")
                }

                // JARファイルが見つからない
                missing.forEach { entry ->
                    sender.sendRichMessage("<yellow>?<reset> ${entry.pluginName} <yellow>(JARファイルが見つかりません)</yellow>")
                }

                // ハッシュ未保存で検証できない
                noHash.forEach { entry ->
                    sender.sendRichMessage("<gray>-<reset> ${entry.pluginName} <gray>(ハッシュ未保存で検証不可)</gray>")
                }

                // サマリ表示
                sender.sendRichMessage(
                    "<gray>検証完了: <green>OK ${ok.size}<gray> / " +
                        "<red>不一致 ${mismatched.size}<gray> / " +
                        "<yellow>欠落 ${missing.size}<gray> / " +
                        "未保存 ${noHash.size}</gray>"
                )
                if (mismatched.isNotEmpty()) {
                    sender.sendRichMessage(
                        "<red>ハッシュ不一致のプラグインは破損または改竄されている可能性があります。" +
                            "'mpm update <plugin>' で再取得してください。</red>"
                    )
                }
            }
        )
    }
}