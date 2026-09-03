/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth.model.job

import kotlinx.serialization.Serializable

/**
 * ジョブ受付（POST /jobs）のリクエストボディ
 *
 * @property type ジョブ種別（`update_all`。大文字小文字は区別しない）
 * @property force trueの場合、api-version非互換でも強制的に更新する
 * @property skipIntegrity trueの場合、整合性検証の不一致を無視して更新を続行する
 */
@Serializable
data class JobCreateRequest(
    val type: String,
    val force: Boolean = false,
    val skipIntegrity: Boolean = false
)