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
import party.morino.mpm.api.core.plugin.ProjectManager
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Flag
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * プロジェクト初期化コマンドのコントローラー
 * プレゼンテーション層とユースケース層の橋渡しを行う
 * mpm init - mpm.jsonを生成し、すべてのプラグインをunmanagedとして追加
 */
@Command("mpm")
@CommandPermission("mpm.command")
class InitCommand : KoinComponent {
    // KoinによるDI
    private val projectManager: ProjectManager by inject()

    /**
     * プロジェクトを初期化し、mpm.jsonを生成するコマンド
     * pluginsディレクトリ内のすべてのプラグインをunmanagedとして追加する
     *
     * @param sender コマンド送信者
     * @param projectName プロジェクト名（デフォルト: "my-server"）
     * @param overwrite 既存のmpm.jsonを上書きするかどうか
     */
    @Command("init")
    suspend fun init(
        sender: CommandSender,
        projectName: String?,
        @Flag("overwrite") overwrite: Boolean = false
    ) {
        // プロジェクト名のデフォルト値を設定
        val name = projectName ?: "my-server"

        // 入力バリデーション
        if (name.isEmpty()) {
            sender.sendMessage("プロジェクト名を入力してください")
            return
        }

        sender.sendMessage("プロジェクトを初期化しています...")

        // ProjectManagerを実行
        projectManager.initialize(name, overwrite).fold(
            // エラーの場合
            { errorMessage ->
                sender.sendMessage("❌ エラー: $errorMessage")
            },
            // 成功の場合
            {
                sender.sendMessage("✅ mpm.jsonを作成しました")
                sender.sendMessage("プロジェクト名: $name")
                sender.sendMessage("すべてのプラグインをunmanagedとして追加しました")
                sender.sendMessage("次のコマンドでプラグインを確認できます: /mpm list")
            }
        )
    }
}