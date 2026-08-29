/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth.model.plugin

import kotlinx.serialization.Serializable
import party.morino.mpm.api.application.plugin.model.detail.PluginDetail

/**
 * プラグイン詳細レスポンス（`mpm info` 相当）
 *
 * リポジトリから取得したプロジェクト情報に、ローカルのインストール状態を付加したもの。
 * web console のプラグイン詳細ページのヘッダ表示に使う。
 *
 * @property name リポジトリ上の表示名
 * @property slug リポジトリ上の識別子（スラッグ）
 * @property source リポジトリ種別（MODRINTH / HANGAR / SPIGOTMC / GITHUB）
 * @property description 概要説明
 * @property homepage プロジェクトページのURL
 * @property license ライセンス表記
 * @property downloads ダウンロード数
 * @property latestVersion リポジトリ上の最新バージョン
 * @property installedVersion インストール済みバージョン（管理下でない場合は null）
 * @property isLocked バージョンロック中かどうか
 */
@Serializable
data class PluginDetailResponse(
    val name: String,
    val slug: String,
    val source: String,
    val description: String?,
    val homepage: String?,
    val license: String?,
    val downloads: Long?,
    val latestVersion: String?,
    val installedVersion: String?,
    val isLocked: Boolean
) {
    companion object {
        /**
         * [PluginDetail] から変換する
         */
        fun from(detail: PluginDetail): PluginDetailResponse =
            PluginDetailResponse(
                name = detail.project.name,
                slug = detail.project.slug,
                source = detail.project.source.name,
                description = detail.project.description,
                homepage = detail.project.homepage,
                license = detail.project.license,
                downloads = detail.project.downloads,
                latestVersion = detail.project.latestVersion,
                installedVersion = detail.installedVersion,
                isLocked = detail.locked
            )
    }
}