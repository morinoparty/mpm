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

package party.morino.mpm.api.application.model.verify

/**
 * 1プラグイン分の整合性再検証結果
 *
 * @param pluginName プラグイン名
 * @param status 判定結果
 * @param expectedSha256 メタデータに保存されていたsha256（なければnull）
 * @param actualSha256 実際のJARから計算したsha256（ファイル欠落・ハッシュ未保存時はnull）
 */
data class VerifyEntry(
    val pluginName: String,
    val status: VerifyStatus,
    val expectedSha256: String? = null,
    val actualSha256: String? = null
)