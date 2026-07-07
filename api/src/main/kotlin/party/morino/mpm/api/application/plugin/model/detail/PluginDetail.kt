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

package party.morino.mpm.api.application.plugin.model.detail

import party.morino.mpm.api.domain.downloader.model.PluginProjectDetail

/**
 * `mpm info` 用のプラグイン詳細
 *
 * プラットフォームから取得したプロジェクト詳細 [project] に、
 * ローカルのインストール状態（管理下の場合）を付加したもの。
 *
 * @param project プラットフォーム提供のプロジェクト詳細
 * @param installedVersion インストール済みバージョン（管理下でない場合はnull）
 * @param locked バージョンロック中かどうか
 */
data class PluginDetail(
    val project: PluginProjectDetail,
    val installedVersion: String? = null,
    val locked: Boolean = false
)