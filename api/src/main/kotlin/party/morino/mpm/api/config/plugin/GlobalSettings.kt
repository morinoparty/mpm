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
 * グローバル設定
 */
@Serializable
data class GlobalSettings(
    // デフォルトの自動更新設定
    val autoUpdate: Boolean = false,

    // デフォルトの自動バージョンチェック設定
    val autoCheck: Boolean = false,

    // デフォルトのバージョンロック設定
    val lock: Boolean = false,

    // プラグインの一時保存ディレクトリ（mpm/配下のパス）
    val tempDir: String = "temp",
)
