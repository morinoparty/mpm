/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.webhook.model

import kotlinx.serialization.Serializable

/**
 * Discord Embedオブジェクト
 * @property title Embedのタイトル
 * @property description Embedの説明文
 * @property color Embedの色（10進数）
 * @property fields Embedのフィールド一覧
 * @property timestamp ISO 8601形式のタイムスタンプ
 * @property footer Embedのフッター
 */
@Serializable
data class DiscordEmbed(
    val title: String,
    val description: String,
    val color: Int,
    val fields: List<DiscordEmbedField> = emptyList(),
    val timestamp: String? = null,
    val footer: Footer? = null
) {
    /**
     * Embedのフッター
     * @property text フッターテキスト
     */
    @Serializable
    data class Footer(val text: String)
}
