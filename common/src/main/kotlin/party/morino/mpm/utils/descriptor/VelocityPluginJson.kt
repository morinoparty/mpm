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
 * velocity-plugin.json の内容（Velocity の注釈処理が生成するプラグイン記述子）
 *
 * 必要な項目だけを定義し、それ以外のキーは読み飛ばす（Utils.json は ignoreUnknownKeys）。
 *
 * @property id プラグインID（必須）
 * @property name 表示名
 * @property version バージョン
 * @property description 説明
 * @property url ウェブサイト
 * @property authors 作者
 * @property dependencies 依存プラグイン
 * @property main メインクラス
 */
@Serializable
data class VelocityPluginJson(
    val id: String,
    val name: String? = null,
    val version: String? = null,
    val description: String? = null,
    val url: String? = null,
    val authors: List<String> = emptyList(),
    val dependencies: List<VelocityPluginJsonDependency> = emptyList(),
    val main: String = ""
)