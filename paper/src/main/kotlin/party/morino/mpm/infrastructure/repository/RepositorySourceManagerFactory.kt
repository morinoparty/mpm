/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.repository

import party.morino.mpm.api.domain.config.ConfigManager
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.repository.RepositoryManager

/**
 * リポジトリソースマネージャーを生成するファクトリー
 */
object RepositorySourceManagerFactory {
    /**
     * プラグインディレクトリとConfigManagerからリポジトリマネージャーを生成
     * config.jsonからリポジトリ設定を読み込む
     *
     * @param pluginDirectory プラグインディレクトリ
     * @param configManager 設定マネージャー
     * @return リポジトリマネージャー
     */
    fun create(
        pluginDirectory: PluginDirectory,
        configManager: ConfigManager
    ): RepositoryManager {
        // rootディレクトリを取得
        val rootDir = pluginDirectory.getRootDirectory()

        // ConfigManagerから設定を取得
        val config = configManager.getConfig()
        val repositoryConfigs = config.repositories

        // リポジトリソースのリストを生成してマネージャーを作成
        val sources = RepositorySourceFactory.createAll(repositoryConfigs, rootDir)
        return RepositoryManagerImpl(sources)
    }
}