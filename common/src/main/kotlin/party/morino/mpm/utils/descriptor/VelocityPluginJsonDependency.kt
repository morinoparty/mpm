/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils.descriptor

import kotlinx.serialization.Serializable

/**
 * velocity-plugin.json の dependencies の1要素
 *
 * @property id 依存先のプラグインID
 * @property optional true の場合はオプション依存
 */
@Serializable
data class VelocityPluginJsonDependency(
    val id: String,
    val optional: Boolean = false
)