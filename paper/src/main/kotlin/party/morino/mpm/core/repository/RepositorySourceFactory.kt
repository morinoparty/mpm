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


