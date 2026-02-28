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

import java.io.File

/**
 * プラグインJARのapi-versionとサーバーの互換性を検証するインターフェース
 *
 * インストール前にプラグインの互換性を確認し、非互換の場合に警告・ブロックするために使用する
 */
interface ApiVersionChecker {
    /**
     * ダウンロード済みのプラグインJARファイルのapi-versionをサーバーのバージョンと比較する
     *
     * @param jarFile プラグインのJARファイル（一時ファイル）
     * @return 互換性チェック結果
     */
    fun checkCompatibility(jarFile: File): CompatibilityResult

    /**
     * サーバーのAPIバージョンを取得する
     *
     * @return サーバーのAPIバージョン文字列（例: "1.21"）
     */
    fun getServerApiVersion(): String
}
