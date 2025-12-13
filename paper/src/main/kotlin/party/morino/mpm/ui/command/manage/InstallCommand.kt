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
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.core.plugin.PluginUpdateManager

/**
 * 一括インストールコマンドのコントローラー
 * プレゼンテーション層とユースケース層の橋渡しを行う
 * mpm install - mpm.jsonに定義されたプラグインを一括インストール
 */
@Command("mpm")
@Permission("mpm.command")
class InstallCommand : KoinComponent {
    // KoinによるDI
    private val updateManager: PluginUpdateManager by inject()

    /**
     * mpm.jsonに定義されたプラグインを一括インストールするコマンド
     * @param sender コマンド送信者
     */
    @Command("install")
    suspend fun install(sender: CommandSender) {
        sender.sendRichMessage("<gray>mpm.jsonを読み込んでいます...")

        // PluginUpdateManagerを実行
        updateManager.installAll().fold(
            // 失敗時の処理
            { errorMessage ->
                sender.sendRichMessage("<red>$errorMessage")
            },
            // 成功時の処理
            { result ->
                // 削除されたプラグイン一覧を表示
                for (removedInfo in result.removed) {
                    sender.sendRichMessage("<red>-<reset> ${removedInfo.name} ${removedInfo.version}")
                }

                // インストールされたプラグイン一覧を表示
                for (installInfo in result.installed) {
                    val versionInfo =
                        if (installInfo.latestVersion.isEmpty() ||
                            installInfo.currentVersion == installInfo.latestVersion
                        ) {
                            installInfo.currentVersion
                        } else {
                            "${installInfo.currentVersion} (${installInfo.latestVersion}が有効)"
                        }
                    sender.sendRichMessage("<green>+<reset> ${installInfo.name} $versionInfo")
                }

                // 失敗したプラグイン一覧を表示
                if (result.failed.isNotEmpty()) {
                    sender.sendRichMessage("<red>以下のプラグインのインストールに失敗しました:")
                    for ((pluginName, errorMessage) in result.failed) {
                        sender.sendRichMessage("<red>  - $pluginName: $errorMessage")
                    }
                }

                // すべて成功またはスキップされた場合
                if (result.installed.isEmpty() && result.removed.isEmpty() && result.failed.isEmpty()) {
                    sender.sendRichMessage("<gray>インストールが必要なプラグインはありません。")
                } else if (result.failed.isEmpty()) {
                    // すべて成功した場合
                    sender.sendRichMessage("<gray>変更を反映するには、サーバーを再起動してください。")
                }
            }
        )
    }
}