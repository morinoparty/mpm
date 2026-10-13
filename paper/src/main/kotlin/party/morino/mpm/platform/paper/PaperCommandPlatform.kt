/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.platform.paper

import net.kyori.adventure.audience.Audience
import party.morino.mpm.api.domain.downloader.model.TargetPlatform
import party.morino.mpm.ui.command.platform.CommandPlatform
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.command.CommandActor

/**
 * Paper 用の [CommandPlatform] 実装
 *
 * Bukkit の CommandSender は [Audience] を実装しているため、そのまま送信先として使う。
 * パーミッションは Bukkit のパーミッション階層（`mpm.command` が子ノードを含む）で判定する。
 */
class PaperCommandPlatform : CommandPlatform {
    /** Paper ではこれまでどおり `/mpm` をルートにする */
    override val rootLabel: String = TargetPlatform.PAPER.commandRoot

    /**
     * Lamp の実行者から Bukkit の CommandSender を取り出す
     *
     * @param actor Lamp の実行者（BukkitLamp では常に [BukkitCommandActor]）
     * @return 実行者の CommandSender
     */
    override fun audienceOf(actor: CommandActor): Audience = (actor as BukkitCommandActor).sender()

    /**
     * Bukkit のパーミッションで判定する
     *
     * 未登録のノードは Bukkit の既定（OP のみ）で判定されるため、従来の `@CommandPermission` と同じ結果になる。
     *
     * @param actor Lamp の実行者
     * @param node パーミッションノード
     * @return 持っていれば true
     */
    override fun hasPermission(
        actor: CommandActor,
        node: String
    ): Boolean = (actor as BukkitCommandActor).sender().hasPermission(node)
}