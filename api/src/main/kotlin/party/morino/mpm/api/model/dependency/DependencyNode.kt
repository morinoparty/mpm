/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
and related and neighboring rights to this software to the public domain worldwide.
This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.model.dependency

import kotlinx.serialization.Serializable

/**
 * 依存関係ツリーのノードを表すデータクラス
 *
 * @property pluginName プラグイン名
 * @property isInstalled インストール済みかどうか
 * @property isRequired 必須依存かどうか（false = softDepend）
 * @property children 子ノード（このプラグインが依存しているプラグイン）
 */
@Serializable
data class DependencyNode(
    val pluginName: String,
    val isInstalled: Boolean,
    val isRequired: Boolean,
    val children: List<DependencyNode> = emptyList()
)