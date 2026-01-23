/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
 * and related and neighboring rights to this software to the public domain worldwide.
 * This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.plugin.model

import kotlinx.serialization.Serializable

/**
 * プラグイン名を表すValue Object
 *
 * プラグイン名の一意性と不変性を保証する
 */
@JvmInline
@Serializable
value class PluginName(
    val value: String
) {
    init {
        require(value.isNotBlank()) { "Plugin name cannot be blank" }
    }

    override fun toString(): String = value
}