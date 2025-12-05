/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.core.repository

import party.morino.mpm.api.config.plugin.RepositorySourceConfig
import party.morino.mpm.api.core.repository.PluginRepositorySource
import party.morino.mpm.api.core.repository.PluginRepositorySourceManager
import party.morino.mpm.api.core.repository.RepositoryFile
import java.io.File

/**
 * リポジトリソースを生成するファクトリ
 */
object RepositorySourceFactory {
    /**
     * 設定からリポジトリソースを生成
     * @param config リポジトリソース設定
     * @param baseDirectory ベースディレクトリ（相対パスの解決に使用）
     * @return 生成されたリポジトリソース
     */
    fun create(
        config: RepositorySourceConfig,
        baseDirectory: File
    ): PluginRepositorySource =
        when (config) {
            is RepositorySourceConfig.Local -> {
                // ローカルソースの場合、相対パスをベースディレクトリから解決
                val directory = File(baseDirectory, config.path)
                LocalRepositorySource(directory)
            }

            is RepositorySourceConfig.Remote -> {
                // リモートソースの場合、URLとヘッダーを使用
                RemoteRepositorySource(config.url, config.headers)
            }
        }

    /**
     * 複数の設定からリポジトリソースのリストを生成
     * @param configs リポジトリソース設定のリスト
     * @param baseDirectory ベースディレクトリ
     * @return 生成されたリポジトリソースのリスト
     */
    fun createAll(
        configs: List<RepositorySourceConfig>,
        baseDirectory: File
    ): List<PluginRepositorySource> = configs.map { create(it, baseDirectory) }
}

/**
 * リポジトリソースマネージャーの実装
 * 複数のリポジトリソースを優先順位順に管理する
 */
class RepositorySourceManager(
    private val sources: List<PluginRepositorySource>
) : PluginRepositorySourceManager {
    /**
     * 利用可能なすべてのプラグインの一覧を取得
     * 複数のソースから重複を除いて返す
     * @return プラグイン名のリスト
     */
    override suspend fun getAvailablePlugins(): List<String> {
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

        return allPlugins.sorted()
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

/**
 * リポジトリファイルの検索結果
 * @property file リポジトリファイルの内容
 * @property source ファイルを提供したソース
 */
data class RepositoryFileResult(
    val file: RepositoryFile,
    val source: PluginRepositorySource
)