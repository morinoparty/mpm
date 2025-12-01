/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.repository

import party.morino.mpm.api.repository.PluginRepositorySource
import party.morino.mpm.api.repository.RepositoryFile

/**
 * リモートURLからリポジトリファイルを取得するソース
 * @property url リポジトリのベースURL
 * @property headers HTTPリクエストに追加するヘッダー
 */
class RemoteRepositorySource(
    private val url: String,
    private val headers: Map<String, String> = emptyMap()
) : PluginRepositorySource {
    /**
     * リモートソースが利用可能かを確認
     * TODO: 実装が必要 - ベースURLにアクセスしてレスポンスを確認
     * @return 利用可能な場合はtrue
     */
    override suspend fun isAvailable(): Boolean {
        TODO("Not yet implemented - リモートソースの可用性確認を実装する")
        // 実装例:
        // - ベースURLにHEADリクエストを送信
        // - レスポンスステータスが200の場合はtrue
        // - タイムアウトやエラーの場合はfalse
    }

    /**
     * 利用可能なプラグインの一覧を取得
     * TODO: 実装が必要 - リモートからプラグイン一覧を取得
     * @return プラグイン名のリスト
     */
    override suspend fun getAvailablePlugins(): List<String> {
        TODO("Not yet implemented - リモートからプラグイン一覧を取得する")
        // 実装例:
        // - {url}/index.json にアクセスしてプラグイン一覧を取得
        // - または {url}/ から利用可能なJSONファイルのリストを取得
        // - プラグイン名のリストを返す
    }

    /**
     * 指定したプラグインのリポジトリファイルを取得
     * TODO: 実装が必要 - リモートから特定のリポジトリファイルを取得
     * @param pluginName プラグイン名
     * @return リポジトリファイルの内容、見つからない場合はnull
     */
    override suspend fun getRepositoryFile(pluginName: String): RepositoryFile? {
        TODO("Not yet implemented - リモートからリポジトリファイルを取得する")
        // 実装例:
        // - {url}/{pluginName}.json にGETリクエストを送信
        // - レスポンスをRepositoryFileにデシリアライズ
        // - エラーの場合はnullを返す
        // - headersをリクエストに追加
    }

    /**
     * リポジトリソースの種類を取得
     * @return "remote"
     */
    override fun getSourceType(): String = "remote"

    /**
     * リポジトリソースの識別子を取得
     * @return ベースURL
     */
    override fun getIdentifier(): String = url
}