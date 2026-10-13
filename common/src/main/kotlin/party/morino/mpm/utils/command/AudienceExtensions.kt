/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils.command

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.minimessage.MiniMessage

/**
 * MiniMessage 形式の文字列を解釈してメッセージを送る
 *
 * Paper の CommandSender と Velocity の CommandSource には同名のメソッドがあるが、
 * Adventure の [Audience] には無いため、共通のコマンドクラスから使えるよう拡張関数として用意する。
 *
 * @param message MiniMessage 形式のメッセージ
 */
fun Audience.sendRichMessage(message: String) {
    sendMessage(MiniMessage.miniMessage().deserialize(message))
}