/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth.model.dependency

import kotlinx.serialization.Serializable
import party.morino.mpm.api.model.dependency.DependencyInfo
import party.morino.mpm.api.model.dependency.DependencyTree

/**
 * プラグインの依存関係レスポンス（`mpm deps` 相当）
 *
 * web console の依存関係タブが1リクエストで描画できるよう、
 * `DependencyService` の tree / info / reverse / why を1つのオブジェクトにまとめる。
 *
 * @property name 対象プラグイン名
 * @property info plugin.yml 由来の依存関係情報
 * @property tree 依存関係ツリー（構築に失敗した場合は null）
 * @property reverseDependencies このプラグインに依存しているプラグインの一覧
 * @property dependencyChains このプラグインへ至る依存経路（`root -> ... -> name` の順）
 */
@Serializable
data class DependencyResponse(
    val name: String,
    val info: DependencyInfo,
    val tree: DependencyTree?,
    val reverseDependencies: List<String>,
    val dependencyChains: List<List<String>>
)