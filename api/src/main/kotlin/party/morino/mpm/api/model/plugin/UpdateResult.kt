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

package party.morino.mpm.api.model.plugin

import kotlinx.serialization.Serializable

/**
 * プラグインの更新結果
 */
@Serializable
data class UpdateResult(
    // プラグイン名
    val pluginName: String,
    // 更新前のバージョン
    val oldVersion: String,
    // 更新後のバージョン
    val newVersion: String,
    // 更新が成功したかどうか
    val success: Boolean,
    // エラーメッセージ（失敗時のみ）
    val errorMessage: String? = null
)