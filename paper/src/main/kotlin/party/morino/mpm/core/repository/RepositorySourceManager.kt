/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.core.repository

import party.morino.mpm.api.core.repository.PluginRepositorySource
import party.morino.mpm.api.core.repository.PluginRepositorySourceManager
import party.morino.mpm.api.core.repository.RepositoryFile

/**
 * リポジトリソースマネージャーの実装
 * 複数のリポジトリソースを優先順位順に管理する
 */
class RepositorySourceManager(
    private val sources: List<PluginRepositorySource>
) : PluginRepositorySourceManager {
    // キャッシュ用のプロパティ
    private var cachedPlugins: List<String>? = null
    private var cacheExpirationTime: Long = 0
    private val cacheTtlMillis = 180_000L // 3分 = 180秒 = 180,000ミリ秒

    /**
     * 利用可能なすべてのプラグインの一覧を取得
     * 複数のソースから重複を除いて返す
     * キャッシュが有効な場合はキャッシュから返す
     * @return プラグイン名のリスト
     */
    override suspend fun getAvailablePlugins(): List<String> {
        // 現在時刻を取得
        val currentTime = System.currentTimeMillis()

        // キャッシュが有効かチェック
        if (cachedPlugins != null && currentTime < cacheExpirationTime) {
            return cachedPlugins!!
        }

        // すべてのソースから利用可能なプラグインを取得
        val allPlugins = mutableSetOf<String>()

        for (source in sources) {
            try {
                if (source.isAvailable()) {
                    val plugins = source.getAvailablePlugins()
                    allPlugins.addAll(plugins)
                }
            } catch (e: Exception) {
                // エラーが発生した場合はスキップ
                continue
            }
        }

        // 結果をソートしてキャッシュに保存
        val result = allPlugins.sorted()
        cachedPlugins = result
        cacheExpirationTime = currentTime + cacheTtlMillis

        return result
    }

    /**
     * 指定したプラグインのリポジトリファイルを取得
     * 優先順位順にソースを検索し、最初に見つかったものを返す
     * @param pluginName プラグイン名
     * @return リポジトリファイルの内容、見つからない場合はnull
     */
    override suspend fun getRepositoryFile(pluginName: String): RepositoryFile? {
        // 優先順位順にソースを検索
        for (source in sources) {
            try {
                if (!source.isAvailable()) {
                    continue
                }

                val file = source.getRepositoryFile(pluginName)
                if (file != null) {
                    return file
                }
            } catch (e: Exception) {
                // エラーが発生した場合は次のソースを試す
                continue
            }
        }

        // どのソースからも見つからなかった場合はnull
        return null
    }

    /**
     * すべてのリポジトリソースを取得
     * @return リポジトリソースのリスト
     */
    override fun getRepositorySources(): List<PluginRepositorySource> = sources

    /**
     * 利用可能なソースの一覧を取得
     * @return 利用可能なソースのリスト
     */
    override suspend fun getAvailableSources(): List<PluginRepositorySource> =
        sources.filter {
            try {
                it.isAvailable()
            } catch (e: Exception) {
                false
            }
        }
}