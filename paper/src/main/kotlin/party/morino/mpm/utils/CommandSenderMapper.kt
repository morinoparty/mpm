/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils

import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.incendo.cloud.SenderMapper

/**
 * CommandSourceStackとCommandSender間のマッピングを提供するクラス
 * PaperプラグインでCloudコマンドフレームワークを使用する際に必要
 */
class CommandSenderMapper : SenderMapper<CommandSourceStack, CommandSender> {
    /**
     * CommandSourceStackをCommandSenderにマッピングする
     * @param source CommandSourceStack
     * @return CommandSender
     */
    override fun map(source: CommandSourceStack): CommandSender = source.sender

    /**
     * CommandSenderをCommandSourceStackにマッピングする
     * @param sender CommandSender
     * @return CommandSourceStack
     */
    override fun reverse(sender: CommandSender): CommandSourceStack {
        return object : CommandSourceStack {
            // コマンド送信者の位置を取得
            override fun getLocation(): Location {
                // エンティティの場合はその位置を返す
                if (sender is Entity) {
                    return sender.location
                }
                // それ以外の場合は最初のワールドの原点を返す
                val worlds = Bukkit.getWorlds()
                return Location(if (worlds.isEmpty()) null else worlds.first(), 0.0, 0.0, 0.0)
            }

            // コマンド送信者を取得
            override fun getSender(): CommandSender = sender

            // 実行者（エンティティ）を取得
            override fun getExecutor(): Entity? = sender as? Entity

            // 位置を変更したCommandSourceStackを返す（未実装）
            override fun withLocation(location: Location): CommandSourceStack = sender as CommandSourceStack

            // 実行者を変更したCommandSourceStackを返す（未実装）
            override fun withExecutor(executor: Entity): CommandSourceStack = sender as CommandSourceStack
        }
    }
}