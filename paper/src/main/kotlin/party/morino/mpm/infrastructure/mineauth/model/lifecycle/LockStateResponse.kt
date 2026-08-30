/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth.model.lifecycle

import kotlinx.serialization.Serializable

/**
 * プラグインのロック / アンロック結果レスポンス
 *
 * @property name 対象プラグイン名
 * @property isLocked 操作後のロック状態
 * @property message 処理結果メッセージ
 */
@Serializable
data class LockStateResponse(
    val name: String,
    val isLocked: Boolean,
    val message: String
)