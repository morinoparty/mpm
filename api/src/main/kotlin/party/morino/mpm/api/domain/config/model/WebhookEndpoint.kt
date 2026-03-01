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
 * 個別のWebhookエンドポイント設定
 * URLごとに通知するイベントを選択できる
 * @property url Discord WebhookのURL
 * @property events このエンドポイントで通知するイベントの有効/無効設定
 */
@Serializable
data class WebhookEndpoint(
    // Discord WebhookのURL
    val url: String,

    // このエンドポイントのイベント通知設定
    val events: WebhookEvents = WebhookEvents()
)
