/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.command

import org.bukkit.command.CommandSender
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.application.scheduler.UpdateScheduler
import party.morino.mpm.api.domain.config.ConfigManager
import party.morino.mpm.api.domain.repository.RepositoryManager
import party.morino.mpm.infrastructure.config.ConfigLoadDiagnostics
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.annotation.CommandPermission

@Command("mpm")
@CommandPermission("mpm.command.reload")
class ReloadCommand : KoinComponent {
    private val configManager: ConfigManager by inject()
    private val configLoadDiagnostics: ConfigLoadDiagnostics by inject()
    private val repositoryManager: RepositoryManager by inject()
    private val updateScheduler: UpdateScheduler by inject()

    @Subcommand("reload")
    suspend fun reload(sender: CommandSender) {
        configManager.reload()
        // リポジトリマネージャーを再構築して新しいリポジトリ設定を反映
        repositoryManager.reload()
        // スケジューラーを再起動して新しい設定を反映
        updateScheduler.restart()

        // config.jsonが読めずデフォルトへフォールバックした場合、緑の成功表示だけを返すと
        // 「設定が無音で失われた」ことに気付けない。コンソールの警告を見ていなくても
        // 分かるよう、原因と対象パスをその場に返す（onEnableは従来どおり既定値で続行する）
        val failure = configLoadDiagnostics.lastLoadFailure
        if (failure != null) {
            sender.sendRichMessage(
                "<yellow>config.jsonを読み込めなかったため、デフォルト設定で再読み込みしました: $failure"
            )
            sender.sendRichMessage("<yellow>ファイルは変更していません。内容を修正してから再度実行してください。")
            return
        }

        sender.sendRichMessage("<green>設定ファイルを再読み込みしました。")
    }
}