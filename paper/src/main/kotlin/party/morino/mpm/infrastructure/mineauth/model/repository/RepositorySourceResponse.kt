/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth.model.repository

import kotlinx.serialization.Serializable
import party.morino.mpm.api.domain.repository.PluginRepositorySource

/**
 * リポジトリソース1件分のレスポンス
 *
 * mpmがプラグイン定義（リポジトリファイル）を読み込む先を表す。
 * 優先順位の高い順に並んだ配列として返される。
 *
 * @property type ソースの種類（"local" / "remote" など）
 * @property identifier ソースの識別子（ローカルパスやURL）
 */
@Serializable
data class RepositorySourceResponse(
    val type: String,
    val identifier: String
) {
    companion object {
        /**
         * PluginRepositorySourceから変換する
         */
        fun from(source: PluginRepositorySource): RepositorySourceResponse =
            RepositorySourceResponse(
                type = source.getSourceType(),
                identifier = source.getIdentifier()
            )
    }
}