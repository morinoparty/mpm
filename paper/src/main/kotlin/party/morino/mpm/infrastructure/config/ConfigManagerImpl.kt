/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.config

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.config.model.ConfigData
import party.morino.mpm.api.domain.config.ConfigManager
import party.morino.mpm.utils.Utils
import java.io.File

/**
 * ConfigManagerの実装
 *
 * config.jsonの読み込み・管理を行う
 * 依存性はKoinによって注入される
 */
class ConfigManagerImpl : ConfigManager, KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()

    private lateinit var configData : ConfigData

    /**
     * 現在の設定を取得する
     *
     * キャッシュがある場合はそれを返し、ない場合はファイルから読み込む
     *
     * @return 現在のConfigData
     */
    override fun getConfig(): ConfigData {
        return configData
    }

    /**
     * config.jsonを再読み込みする
     *
     * ファイルから設定を読み込み直し、キャッシュを更新する
     */
    override suspend fun reload() {
        configData = loadConfigFromFile()
    }

    /**
     * config.jsonをファイルから読み込む
     *
     * ファイルが存在しない場合はデフォルト値を使用し、ファイルを作成する
     *
     * @return 読み込んだConfigData
     */
    private fun loadConfigFromFile(): ConfigData {
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "config.json")

        // ファイルが存在しない場合はデフォルト値を使用
        if (!configFile.exists()) {
            val defaultConfig = ConfigData()
            saveConfigToFile(defaultConfig)
            return defaultConfig
        }

        val jsonString = configFile.readText()
        val config = Utils.json.decodeFromString<ConfigData>(jsonString)
        return config
    }

    /**
     * ConfigDataをconfig.jsonに保存する
     *
     * @param config 保存するConfigData
     */
    private fun saveConfigToFile(config: ConfigData) {
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "config.json")

        val jsonString = Utils.json.encodeToString(config)
        configFile.writeText(jsonString)
    }
}
