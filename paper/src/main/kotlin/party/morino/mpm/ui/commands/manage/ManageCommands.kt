/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.commands.manage

import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent

/**
 * プラグインインストールコマンドのコントローラー
 * プレゼンテーション層とユースケース層の橋渡しを行う
 * mpm install - mpm-config.jsonに記載されているプラグインを一括インストール
 */
@Command("mpm")
@Permission("mpm.command")
class ManageCommands : KoinComponent {
    // KoinによるDI
    // TODO: 一括インストール用のユースケースを実装する必要がある
    // private val pluginInstallUseCase: PluginInstallUseCase by inject()

    /**
     * mpm-config.jsonに記載されているプラグインを一括インストールするコマンド
     * @param sender コマンド送信者
     */
    @Command("install")
    suspend fun install(sender: CommandSender) {
        // TODO: 一括インストール機能を実装
        sender.sendMessage("一括インストール機能は現在実装中です")
    }
}