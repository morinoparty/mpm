/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.webhook.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Discord Webhook APIに送信するペイロード
 * @property embeds Embedオブジェクトのリスト
 * @property allowedMentions メンション制御（@everyone等の悪用防止）
 */
@Serializable
data class DiscordWebhookPayload(
    val embeds: List<DiscordEmbed>,
    // メンション無効化: parseを空にすることで@everyone等が解釈されなくなる
    @SerialName("allowed_mentions")
    val allowedMentions: AllowedMentions = AllowedMentions()
) {
    /**
     * Discord APIのallowed_mentions構造
     * parseを空リストにすることで全メンションを無効化
     */
    @Serializable
    data class AllowedMentions(
        val parse: List<String> = emptyList()
    )
}
