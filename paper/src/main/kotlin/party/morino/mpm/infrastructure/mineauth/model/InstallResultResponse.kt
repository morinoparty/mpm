/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth.model

import kotlinx.serialization.Serializable
import party.morino.mpm.api.application.model.InstallResult

/**
 * プラグインインストール結果レスポンス
 * InstallResultはSerializableでないためHTTP API用に変換する
 */
@Serializable
data class InstallResultResponse(
    // インストールされたプラグイン名
    val name: String,
    // インストールされたバージョン
    val currentVersion: String,
    // リポジトリ上の最新バージョン
    val latestVersion: String,
    // 削除された古いファイル名（存在する場合）
    val removedVersion: String?
) {
    companion object {
        /**
         * InstallResultドメインオブジェクトから変換する
         */
        fun from(result: InstallResult): InstallResultResponse =
            InstallResultResponse(
                name = result.installed.name,
                currentVersion = result.installed.currentVersion,
                latestVersion = result.installed.latestVersion,
                removedVersion = result.removed?.version
            )
    }
}