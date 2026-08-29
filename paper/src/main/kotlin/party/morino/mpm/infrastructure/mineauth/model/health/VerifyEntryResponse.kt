/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth.model.health

import kotlinx.serialization.Serializable
import party.morino.mpm.api.application.model.verify.VerifyEntry

/**
 * 1プラグイン分の整合性再検証結果レスポンス（`mpm verify` 相当）
 *
 * @property name プラグイン名
 * @property status 判定結果（OK / MISMATCH / NO_HASH / FILE_MISSING）
 * @property expectedSha256 メタデータに保存されていたsha256
 * @property actualSha256 実際のJARから計算したsha256
 */
@Serializable
data class VerifyEntryResponse(
    val name: String,
    val status: String,
    val expectedSha256: String?,
    val actualSha256: String?
) {
    companion object {
        /**
         * VerifyEntryから変換する
         */
        fun from(entry: VerifyEntry): VerifyEntryResponse =
            VerifyEntryResponse(
                name = entry.pluginName,
                status = entry.status.name,
                expectedSha256 = entry.expectedSha256,
                actualSha256 = entry.actualSha256
            )
    }
}