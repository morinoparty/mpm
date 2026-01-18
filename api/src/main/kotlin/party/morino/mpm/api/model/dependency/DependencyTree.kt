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
 * プラグインの依存関係ツリー構造を表すデータクラス
 *
 * @property root ルートノード（対象プラグイン）
 * @property missingRequired 不足している必須依存プラグインのリスト
 * @property missingSoft 不足しているオプション依存プラグインのリスト
 */
@Serializable
data class DependencyTree(
    val root: DependencyNode,
    val missingRequired: List<String>,
    val missingSoft: List<String>
)