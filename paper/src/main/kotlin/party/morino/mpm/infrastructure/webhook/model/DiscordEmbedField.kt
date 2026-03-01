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
 * Discord Embedのフィールド
 * @property name フィールド名
 * @property value フィールド値
 * @property inline インラインで表示するかどうか
 */
@Serializable
data class DiscordEmbedField(
    val name: String,
    val value: String,
    val inline: Boolean = true
)
