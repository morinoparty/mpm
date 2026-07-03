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

package party.morino.mpm.api.application.model.add

import party.morino.mpm.api.application.model.install.InstallResult

/**
 * 個別のプラグイン追加結果
 *
 * @property pluginName プラグイン名
 * @property installResult インストール結果
 * @property isDependency 依存関係として追加されたかどうか（trueの場合は依存、falseの場合はメインプラグイン）
 */
data class PluginAddResult(
    val pluginName: String,
    val installResult: InstallResult,
    val isDependency: Boolean
)