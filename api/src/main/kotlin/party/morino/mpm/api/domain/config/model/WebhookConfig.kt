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
 * 複数のエンドポイントを設定でき、各エンドポイントごとに通知するイベントを選択できる
 * @property enabled Webhook通知のマスタースイッチ
 * @property endpoints Webhookエンドポイントのリスト
 */
@Serializable
data class WebhookConfig(
    // Webhook通知のマスタースイッチ（falseで全エンドポイントを無効化）
    val enabled: Boolean = false,

    // Webhookエンドポイントのリスト（URLごとに通知イベントを設定）
    val endpoints: List<WebhookEndpoint> = emptyList()
)
