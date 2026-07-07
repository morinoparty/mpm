/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils

import net.kyori.adventure.text.minimessage.MiniMessage

/**
 * MiniMessageのタグとして解釈されないよう、外部由来の文字列をエスケープする
 *
 * `sender.sendRichMessage(...)` はMiniMessageを解釈するため、リポジトリ検索結果の
 * プラグイン名・説明などの信頼できない文字列をそのまま埋め込むと、`<red>` や
 * `<click>` などのタグが誤って解釈され、出力の破壊やタグ注入につながる。
 * このヘルパーでエスケープしてから埋め込むことでそれを防ぐ。
 *
 * @return タグがリテラルとして表示される、エスケープ済みの文字列
 */
fun String.escapeMiniMessage(): String = MiniMessage.miniMessage().escapeTags(this)