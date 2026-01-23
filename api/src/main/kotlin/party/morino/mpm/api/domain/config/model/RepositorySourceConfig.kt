/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.config.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * リポジトリソースの設定
 * プラグインのリポジトリファイルを取得する場所を定義する
 */
@Serializable
sealed class RepositorySourceConfig {
    /**
     * ローカルファイルシステムからリポジトリファイルを取得
     * @property path リポジトリディレクトリのパス（mpm/配下の相対パス）
     */
    @Serializable
    @SerialName("local")
    data class Local(
        val path: String = "repository"
    ) : RepositorySourceConfig()

    /**
     * リモートURLからリポジトリファイルを取得
     * @property url リポジトリのベースURL
     * @property headers リクエストに追加するHTTPヘッダー（オプション）
     */
    @Serializable
    @SerialName("remote")
    data class Remote(
        val url: String,
        val headers: Map<String, String> = emptyMap()
    ) : RepositorySourceConfig()
}
