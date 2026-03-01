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
 * 各イベントごとのWebhook通知有効/無効設定
 * デフォルトではすべてのイベントが有効
 */
@Serializable
data class WebhookEvents(
    // プラグインインストール時の通知
    val install: Boolean = true,

    // プラグイン更新時の通知
    val update: Boolean = true,

    // プラグイン管理対象除外時の通知
    val remove: Boolean = true,

    // プラグインアンインストール時の通知
    val uninstall: Boolean = true,

    // プラグインロック時の通知
    val lock: Boolean = true,

    // プラグインロック解除時の通知
    val unlock: Boolean = true,

    // プラグイン更新可能検出時の通知
    val outdated: Boolean = true
)
