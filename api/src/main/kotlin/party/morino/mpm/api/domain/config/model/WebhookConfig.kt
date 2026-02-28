/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.config.model

import kotlinx.serialization.Serializable

/**
 * Discord Webhook通知の設定
 * @property enabled Webhook通知が有効かどうか
 * @property url Discord WebhookのURL
 * @property events 各イベントごとの通知有効/無効設定
 */
@Serializable
data class WebhookConfig(
    // Webhook通知の有効/無効
    val enabled: Boolean = false,

    // Discord WebhookのURL
    val url: String = "",

    // イベントごとの通知設定
    val events: WebhookEvents = WebhookEvents()
)
