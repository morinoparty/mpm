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
import party.morino.mpm.api.application.health.DoctorReport
import party.morino.mpm.api.application.health.DoctorService
import party.morino.mpm.utils.escapeMiniMessage
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Description
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * サーバー健全性診断コマンドのコントローラー
 *
 * mpm doctor - 依存関係・整合性・更新・ロック・管理外を一括診断して要約表示する
 */
@Command("mpm")
@CommandPermission("mpm.command.list")
class DoctorCommand : KoinComponent {
    // Koinによる依存性注入
    private val doctorService: DoctorService by inject()

    /**
     * サーバーのプラグイン管理状態を診断するコマンド
     *
     * @param sender コマンド送信者
     */
    @Subcommand("doctor")
    @Description("プラグイン管理状態を一括診断します。")
    suspend fun doctor(sender: CommandSender) {
        sender.sendRichMessage("<gray>プラグイン管理状態を診断しています...</gray>")

        doctorService.diagnose().fold(
            { error ->
                sender.sendRichMessage("<red>${error.message}</red>")
            },
            { report ->
                render(sender, report)
            }
        )
    }

    /**
     * 診断結果をセクションごとに色分けして表示する
     */
    private fun render(
        sender: CommandSender,
        report: DoctorReport
    ) {
        // プラグイン名・バージョン等はリポジトリ/plugin.yml由来の信頼できない文字列のため、
        // MiniMessageタグとして解釈されないようエスケープして表示する
        sender.sendRichMessage("<green>=== mpm doctor ===</green>")

        // 🔴 ハッシュ不一致（改竄・破損の可能性）
        if (report.hashMismatches.isNotEmpty()) {
            sender.sendRichMessage("<red>✗ 整合性エラー (ハッシュ不一致):</red>")
            report.hashMismatches.forEach { sender.sendRichMessage("<red>  - ${it.escapeMiniMessage()}</red>") }
            sender.sendRichMessage("<gray>    → 'mpm update <plugin>' で再取得してください。</gray>")
        }

        // 🔴 JARファイル欠落
        if (report.fileMissing.isNotEmpty()) {
            sender.sendRichMessage("<red>✗ JARファイルが見つかりません:</red>")
            report.fileMissing.forEach { sender.sendRichMessage("<red>  - ${it.escapeMiniMessage()}</red>") }
            sender.sendRichMessage("<gray>    → 'mpm install' で再インストールしてください。</gray>")
        }

        // 🔴 不足している必須依存
        if (report.missingDependencies.isNotEmpty()) {
            sender.sendRichMessage("<red>✗ 不足している必須依存:</red>")
            report.missingDependencies.forEach { (pluginName, missing) ->
                val deps = missing.joinToString(", ") { it.escapeMiniMessage() }
                sender.sendRichMessage("<red>  - ${pluginName.escapeMiniMessage()} → $deps</red>")
            }
            sender.sendRichMessage("<gray>    → 'mpm deps check' で確認、'mpm add <dep>' で追加してください。</gray>")
        }

        // 🟡 ロックファイルのドリフト
        if (report.missingFromLock.isNotEmpty() || report.staleLockEntries.isNotEmpty()) {
            sender.sendRichMessage("<yellow>⚠ ロックファイルのドリフト:</yellow>")
            report.missingFromLock.forEach {
                sender.sendRichMessage("<yellow>  - ${it.escapeMiniMessage()} (mpm.jsonにあるがロックに未記録)</yellow>")
            }
            report.staleLockEntries.forEach {
                sender.sendRichMessage("<yellow>  - ${it.escapeMiniMessage()} (ロックにあるが管理下でない)</yellow>")
            }
            sender.sendRichMessage("<gray>    → 'mpm install' でロックを再生成してください。</gray>")
        }

        // 🟡 更新可能（情報）
        if (report.outdatedPlugins.isNotEmpty()) {
            sender.sendRichMessage("<yellow>⚠ 更新があります:</yellow>")
            report.outdatedPlugins.forEach {
                val name = it.pluginName.escapeMiniMessage()
                val cur = it.currentVersion.escapeMiniMessage()
                val latest = it.latestVersion.escapeMiniMessage()
                sender.sendRichMessage("<yellow>  - $name: $cur → $latest</yellow>")
            }
            sender.sendRichMessage("<gray>    → 'mpm update' で更新できます。</gray>")
        }

        // 🟡 管理外プラグイン（情報）
        if (report.unmanagedPlugins.isNotEmpty()) {
            sender.sendRichMessage("<yellow>⚠ 管理外のプラグイン (${report.unmanagedPlugins.size}):</yellow>")
            report.unmanagedPlugins.forEach { sender.sendRichMessage("<gray>  - ${it.escapeMiniMessage()}</gray>") }
            sender.sendRichMessage("<gray>    → 'mpm adopt' で管理下に取り込めます。</gray>")
        }

        // ℹ チェックを完了できなかった項目（ネットワーク一時障害など。異常ではない）
        if (report.warnings.isNotEmpty()) {
            sender.sendRichMessage("<gray>ℹ 一部のチェックを完了できませんでした:</gray>")
            report.warnings.forEach { sender.sendRichMessage("<gray>  - ${it.escapeMiniMessage()}</gray>") }
        }

        // サマリ
        sender.sendRichMessage("<gray>${"=".repeat(30)}</gray>")
        when {
            report.hasProblems ->
                sender.sendRichMessage("<red>対処が必要な項目があります。上記のヒントを参照してください。</red>")
            report.outdatedPlugins.isEmpty() && report.unmanagedPlugins.isEmpty() && report.warnings.isEmpty() ->
                sender.sendRichMessage("<green>✓ 問題は見つかりませんでした。すべて健全です。</green>")
            else ->
                sender.sendRichMessage("<green>✓ 異常はありません（更新・管理外・未完了チェックは情報です）。</green>")
        }
    }
}