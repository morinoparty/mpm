/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.model.plugin

import kotlinx.serialization.Serializable

/**
 * Velocity プラグインの依存関係（velocity-plugin.json の dependencies の1要素）
 *
 * @property id 依存先のプラグインID
 * @property optional true の場合はオプション依存（存在しなくても読み込まれる）
 */
@Serializable
data class VelocityDependency(
    val id: String,
    val optional: Boolean = false
)