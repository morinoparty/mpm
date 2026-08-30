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
 * バージョン切り替えリクエストボディ
 *
 * アップグレードとダウングレードを区別せず、「このプラグインをバージョン X にする」という
 * 1つの操作として扱う。
 *
 * @property version 切り替え先バージョン（raw / normalized のどちらでも解決を試みる）
 * @property force trueの場合、ロック済み・api-version非互換でも強制的に切り替える
 * @property skipIntegrity trueの場合、整合性検証の不一致を無視して続行する
 */
@Serializable
data class VersionSwitchRequest(
    val version: String,
    val force: Boolean = false,
    val skipIntegrity: Boolean = false
)