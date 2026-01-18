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
 * プラグインの依存関係情報を表すデータクラス
 *
 * @property pluginName プラグイン名
 * @property depend 必須依存プラグインのリスト
 * @property softDepend オプション依存プラグインのリスト
 * @property loadBefore このプラグインより先に読み込むべきプラグインのリスト
 */
@Serializable
data class DependencyInfo(
    val pluginName: String,
    val depend: List<String>,
    val softDepend: List<String>,
    val loadBefore: List<String>
)