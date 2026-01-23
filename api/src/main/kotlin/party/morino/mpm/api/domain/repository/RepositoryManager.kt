/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
and related and neighboring rights to this software to the public domain worldwide.
This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.repository

/**
 * リポジトリソースマネージャーのインターフェース
 * 複数のリポジトリソースを優先順位順に管理する
 */
interface RepositoryManager {
    /**
     * 利用可能なすべてのプラグインの一覧を取得
     * 複数のソースから重複を除いて返す
     * @return プラグイン名のリスト
     */
    suspend fun getAvailablePlugins(): List<String>

    /**
     * 指定したプラグインのリポジトリファイルを取得
     * 優先順位順にソースを検索し、最初に見つかったものを返す
     * @param pluginName プラグイン名
     * @return リポジトリファイルの内容、見つからない場合はnull
     */
    suspend fun getRepositoryFile(pluginName: String): RepositoryFile?

    /**
     * 利用可能なソースの一覧を取得
     * @return 利用可能なソースのリスト
     */
    suspend fun getAvailableSources(): List<PluginRepositorySource>

    /**
     * すべてのリポジトリソースを取得
     * @return リポジトリソースのリスト
     */
    fun getRepositorySources(): List<PluginRepositorySource>
}