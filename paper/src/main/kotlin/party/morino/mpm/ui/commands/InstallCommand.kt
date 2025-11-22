/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.commands

import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.Default
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.core.plugin.PluginInstallUseCase

/**
 * プラグインインストールコマンドのコントローラー
 * プレゼンテーション層とユースケース層の橋渡しを行う
 */
@Command("mpm")
@Permission("mpm.command")
class InstallCommand : KoinComponent {
    // KoinによるDI
    private val pluginInstallUseCase: PluginInstallUseCase by inject()

    /**
     * プラグインをインストールするコマンド
     * @param sender コマンド送信者
     * @param repositoryUrl リポジトリURL
     * @param number ダウンロードする番号（複数ファイルがある場合）
     */
    @Command("install <repositoryUrl> [number]")
    suspend fun install(
        sender: CommandSender,
        @Argument("repositoryUrl") repositoryUrl: String,
        @Argument("number") @Default("1") number: Int?
    ) {
        // 入力バリデーション
        if (repositoryUrl.isEmpty()) {
            sender.sendMessage("リポジトリのURLを入力してください")
            return
        }

        // ユースケースの実行
        val result = pluginInstallUseCase.installPlugin(repositoryUrl, number)

        // 結果に応じたメッセージを表示
        if (result) {
            sender.sendMessage("プラグインのインストールに成功しました")
        } else {
            sender.sendMessage("プラグインのインストールに失敗しました")
        }
    }
}