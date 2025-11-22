/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.config.plugin

import kotlinx.serialization.Serializable

/**
 * プラグイン固有の設定
 */
@Serializable
data class PluginSettings(
    // バージョンをロックするか（trueの場合、updateコマンドでも更新されない）
    val lock: Boolean = false,

    // 自動更新を有効にするか（将来実装予定）
    val autoUpdate: Boolean = false,

    // カスタムダウンロードURL（オプション）
    val downloadUrl: String? = null,

    // カスタムファイル名（オプション）
    val fileName: String? = null,
)
