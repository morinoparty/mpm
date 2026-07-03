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

package party.morino.mpm.api.application.model.install

/**
 * プラグインインストール情報
 */
data class PluginInstallInfo(
    // プラグイン名
    val name: String,
    // 現在のバージョン（raw形式）
    val currentVersion: String,
    // 最新バージョン（raw形式）
    val latestVersion: String
)