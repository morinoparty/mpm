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
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.core.config.ConfigManager

@Command("mpm")
@Permission("mpm.command")
class ReloadCommand: KoinComponent {

    private val configManager : ConfigManager by inject()
    @Command("reload")
    suspend fun reload(sender : CommandSender) {
        configManager.reload()
        sender.sendRichMessage("<green>設定ファイルを再読み込みしました。")
    }
}