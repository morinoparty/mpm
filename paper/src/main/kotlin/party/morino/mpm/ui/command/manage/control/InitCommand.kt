/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.command.manage.control

import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.application.project.ProjectService
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.domain.plugin.model.PluginSpec
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.annotation.Switch
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * プロジェクト初期化コマンドのコントローラー
 * プレゼンテーション層とユースケース層の橋渡しを行う
 * mpm init - mpm.jsonを生成し、すべてのプラグインをunmanagedとして追加（mpm 自身は latest で管理）
 */
@Command("mpm")
@CommandPermission("mpm.command.init")
class InitCommand : KoinComponent {
    // KoinによるDI
    private val projectService: ProjectService by inject()
    private val plugin: JavaPlugin by inject()

    /**
     * プロジェクトを初期化し、mpm.jsonを生成するコマンド
     * pluginsディレクトリ内のすべてのプラグインをunmanagedとして追加する
     *
     * @param sender コマンド送信者
     * @param overwrite 既存のmpm.jsonを上書きするかどうか
     */
    @Subcommand("init")
    suspend fun init(
        sender: CommandSender,
        @Switch("overwrite")overwrite: Boolean = false
    ) {
        sender.sendRichMessage("<gray>プロジェクトを初期化しています...</gray>")

        // ProjectServiceを実行（overwriteフラグを渡す）
        projectService.init("server", overwrite).fold(
            // エラーの場合
            { error ->
                sender.sendRichMessage("<red>エラー: ${error.message}</red>")
            },
            // 成功の場合
            { project ->
                sender.sendRichMessage("<green>mpm.jsonを作成しました</green>")
                sender.sendRichMessage("<white>すべてのプラグインをunmanagedとして追加しました</white>")
                // mpm 自身が管理対象として登録できたかを伝える（失敗理由はコンソールに出力済み）
                if (project.getPluginSpec(PluginName(plugin.name)) is PluginSpec.Managed) {
                    sender.sendRichMessage("<white>${plugin.name} 自身を latest の管理対象として追加しました</white>")
                } else {
                    sender.sendRichMessage(
                        "<yellow>${plugin.name} 自身を管理対象に登録できませんでした。詳細はコンソールを確認してください</yellow>"
                    )
                }
                sender.sendRichMessage("<gray>次のコマンドでプラグインを確認できます: /mpm list</gray>")
            }
        )
    }
}