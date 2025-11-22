/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.core.plugin

import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.core.plugin.PluginListUseCase
import party.morino.mpm.api.core.plugin.PluginRepository
import party.morino.mpm.api.model.plugin.PluginData

/**
 * プラグインリスト表示に関するユースケースの実装
 * 依存性はKoinによって注入される
 */
class PluginListUseCaseImpl :
    PluginListUseCase,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginRepository: PluginRepository by inject()
    private val plugin: JavaPlugin by inject()

    /**
     * 管理下のすべてのプラグインを取得
     * @return 管理下のプラグインのリスト
     */
    override suspend fun getAllManagedPlugins(): List<PluginData> = pluginRepository.getAllManagedPluginData()

    /**
     * サーバー上の全プラグインの状態を取得
     * @return プラグイン名とその状態（有効/無効）のマップ
     */
    override fun getAllServerPlugins(): Map<String, Boolean> =
        plugin.server.pluginManager.plugins.associate {
            it.name to it.isEnabled
        }

    /**
     * 管理下にないプラグインを取得
     * @return 管理下にないプラグイン名のリスト
     */
    override suspend fun getUnmanagedPlugins(): List<String> {
        val managedPlugins =
            getAllManagedPlugins()
                .map {
                    when (it) {
                        is PluginData.BukkitPluginData -> it.name
                        is PluginData.PaperPluginData -> it.name
                    }
                }.toSet()

        return getAllServerPlugins().keys.filter {
            it !in managedPlugins && it != plugin.name
        }
    }

    /**
     * 有効なプラグインのみを取得
     * @return 有効なプラグインのリスト
     */
    override suspend fun getEnabledPlugins(): List<PluginData> {
        val enabledPluginNames =
            getAllServerPlugins()
                .filter { it.value }
                .map { it.key }
                .toSet()

        return getAllManagedPlugins().filter {
            val name =
                when (it) {
                    is PluginData.BukkitPluginData -> it.name
                    is PluginData.PaperPluginData -> it.name
                }
            name in enabledPluginNames
        }
    }

    /**
     * 無効なプラグインのみを取得
     * @return 無効なプラグインのリスト
     */
    override suspend fun getDisabledPlugins(): List<PluginData> {
        val disabledPluginNames =
            getAllServerPlugins()
                .filter { !it.value }
                .map { it.key }
                .toSet()

        return getAllManagedPlugins().filter {
            val name =
                when (it) {
                    is PluginData.BukkitPluginData -> it.name
                    is PluginData.PaperPluginData -> it.name
                }
            name in disabledPluginNames
        }
    }
}