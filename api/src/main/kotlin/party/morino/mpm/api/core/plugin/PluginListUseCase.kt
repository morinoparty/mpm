/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.core.plugin

import party.morino.mpm.api.model.plugin.PluginData

/**
 * プラグインリスト表示に関するユースケース
 */
interface PluginListUseCase {
    /**
     * 管理下のすべてのプラグインを取得
     * @return 管理下のプラグインのリスト
     */
    suspend fun getAllManagedPlugins(): List<PluginData>

    /**
     * サーバー上の全プラグインの状態を取得
     * @return プラグイン名とその状態（有効/無効）のマップ
     */
    fun getAllServerPlugins(): Map<String, Boolean>

    /**
     * 管理下にないプラグインを取得
     * @return 管理下にないプラグイン名のリスト
     */
    suspend fun getUnmanagedPlugins(): List<String>

    /**
     * 有効なプラグインのみを取得
     * @return 有効なプラグインのリスト
     */
    suspend fun getEnabledPlugins(): List<PluginData>

    /**
     * 無効なプラグインのみを取得
     * @return 無効なプラグインのリスト
     */
    suspend fun getDisabledPlugins(): List<PluginData>
}