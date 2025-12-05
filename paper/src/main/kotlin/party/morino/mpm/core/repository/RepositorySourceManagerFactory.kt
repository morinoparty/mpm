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
import party.morino.mpm.api.config.plugin.MpmConfig
import party.morino.mpm.api.config.plugin.RepositorySourceConfig
import party.morino.mpm.api.core.repository.PluginRepositorySourceManager
import party.morino.mpm.api.utils.Utils
import java.io.File

/**
 * リポジトリソースマネージャーを生成するファクトリー
 */
object RepositorySourceManagerFactory {
    /**
     * プラグインディレクトリからリポジトリソースマネージャーを生成
     * mpm.jsonが存在する場合はそこから設定を読み込み、存在しない場合はデフォルト設定を使用
     *
     * @param pluginDirectory プラグインディレクトリ
     * @return リポジトリソースマネージャー
     */
    fun create(pluginDirectory: PluginDirectory): PluginRepositorySourceManager {
        // rootディレクトリとmpm.jsonを取得
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "mpm.json")

        // mpm.jsonが存在する場合は読み込む、存在しない場合はデフォルト設定を使用
        val repositoryConfigs =
            if (configFile.exists()) {
                try {
                    // mpm.jsonを読み込んでリポジトリ設定を取得
                    val jsonString = configFile.readText()
                    val mpmConfig = Utils.json.decodeFromString<MpmConfig>(jsonString)
                    mpmConfig.repositories
                } catch (e: Exception) {
                    // 読み込みに失敗した場合はデフォルト設定を使用
                    listOf(RepositorySourceConfig.Local())
                }
            } else {
                // mpm.jsonが存在しない場合はデフォルト設定を使用
                listOf(RepositorySourceConfig.Local())
            }

        // リポジトリソースのリストを生成してマネージャーを作成
        val sources = RepositorySourceFactory.createAll(repositoryConfigs, rootDir)
        return RepositorySourceManager(sources)
    }
}