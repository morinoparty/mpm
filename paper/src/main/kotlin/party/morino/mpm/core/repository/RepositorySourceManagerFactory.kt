/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.core.repository

import party.morino.mpm.api.config.PluginDirectory
import party.morino.mpm.api.core.config.ConfigManager
import party.morino.mpm.api.core.repository.PluginRepositorySourceManager

/**
 * リポジトリソースマネージャーを生成するファクトリー
 */
object RepositorySourceManagerFactory {
    /**
     * プラグインディレクトリとConfigManagerからリポジトリソースマネージャーを生成
     * config.jsonからリポジトリ設定を読み込む
     *
     * @param pluginDirectory プラグインディレクトリ
     * @param configManager 設定マネージャー
     * @return リポジトリソースマネージャー
     */
    fun create(
        pluginDirectory: PluginDirectory,
        configManager: ConfigManager
    ): PluginRepositorySourceManager {
        // rootディレクトリを取得
        val rootDir = pluginDirectory.getRootDirectory()

        // ConfigManagerから設定を取得
        val config = configManager.getConfig()
        val repositoryConfigs = config.repositories

        // リポジトリソースのリストを生成してマネージャーを作成
        val sources = RepositorySourceFactory.createAll(repositoryConfigs, rootDir)
        return RepositorySourceManager(sources)
    }
}