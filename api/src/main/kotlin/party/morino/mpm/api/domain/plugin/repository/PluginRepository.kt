/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.plugin.repository

import party.morino.mpm.api.model.plugin.PluginData
import java.io.File

/**
 * プラグインリポジトリのインターフェース
 * プラグインの保存と取得に関する操作を定義
 */
interface PluginRepository {
    /**
     * 指定された名前のプラグインを取得
     * @param name プラグイン名
     * @return プラグインデータ、存在しない場合はnull
     */
    suspend fun getManagedPluginData(name: String): PluginData?

    /**
     * すべてのプラグインを取得
     * @return 全プラグインのリスト
     */
    suspend fun getAllManagedPluginData(): List<PluginData>

    /**
     * プラグインを保存
     * @param plugin 保存するプラグインデータ
     * @param file プラグインファイル
     */
    suspend fun savePlugin(
        plugin: PluginData,
        file: File
    )

    /**
     * プラグインを削除
     * @param name 削除するプラグイン名
     * @return 削除に成功した場合はtrue
     */
    suspend fun removePlugin(name: String): Boolean

    /**
     * プラグインの存在確認
     * @param name 確認するプラグイン名
     * @return 存在する場合はtrue
     */
    suspend fun exists(name: String): Boolean
}