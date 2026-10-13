/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.command.platform

import net.kyori.adventure.audience.Audience
import revxrsal.commands.command.CommandActor

/**
 * コマンドの実行者をプラットフォームごとに解釈するための境界
 *
 * 共通のコマンドクラスは送信者を [Audience] として受け取り、パーミッションは [hasPermission] で判定する。
 * Paper では Bukkit の CommandSender、Velocity では CommandSource がそれぞれ [Audience] を実装しているため、
 * 各プラットフォームの実装はその値を取り出して返すだけでよい。
 */
interface CommandPlatform {
    /** コマンドのルート名（Paper は `mpm`、Velocity は `mpmv`） */
    val rootLabel: String

    /**
     * Lamp の実行者から、メッセージの送信先を取り出す
     *
     * @param actor Lamp の実行者
     * @return 実行者に対応するメッセージの送信先
     */
    fun audienceOf(actor: CommandActor): Audience

    /**
     * 実行者がパーミッションノードを持っているかを判定する
     *
     * @param actor Lamp の実行者
     * @param node パーミッションノード
     * @return 持っていれば true
     */
    fun hasPermission(
        actor: CommandActor,
        node: String
    ): Boolean
}