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

package party.morino.mpm.api.domain.compatibility

/**
 * APIバージョン互換性チェックの結果を表すドメインモデル
 *
 * プラグインのapi-versionとサーバーのAPIバージョンの比較結果を表現する
 */
sealed class CompatibilityResult {
    /** 互換性あり（プラグインのapi-versionがサーバー以下） */
    data object Compatible : CompatibilityResult()

    /** 互換性なし（プラグインのapi-versionがサーバーより新しい） */
    data class Incompatible(
        // プラグインが要求するapi-version（例: "1.21"）
        val pluginApiVersion: String,
        // サーバーのAPIバージョン（例: "1.20"）
        val serverApiVersion: String
    ) : CompatibilityResult()

    /** 判定不能（api-versionが未指定、パース不能など） */
    data class Unknown(
        // 判定不能の理由
        val reason: String
    ) : CompatibilityResult()
}
