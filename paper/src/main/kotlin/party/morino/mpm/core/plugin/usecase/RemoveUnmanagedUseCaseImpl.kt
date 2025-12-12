/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.core.plugin.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.config.PluginDirectory
import party.morino.mpm.api.config.plugin.MpmConfig
import party.morino.mpm.api.core.plugin.RemoveUnmanagedUseCase
import party.morino.mpm.utils.PluginDataUtils
import party.morino.mpm.utils.Utils
import java.io.File

/**
 * mpm removeUnmanagedコマンドに関するユースケースの実装
 * 依存性はKoinによって注入される
 */
class RemoveUnmanagedUseCaseImpl :
    RemoveUnmanagedUseCase,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()

    /**
     * mpm管理下にないプラグインを削除する
     * mpm.jsonに含まれていないプラグインのJARファイルを削除する
     *
     * @return 成功時は削除されたプラグイン名のリスト、失敗時はエラーメッセージ
     */
    override suspend fun removeUnmanaged(): Either<String, List<String>> {
        // rootディレクトリを取得
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "mpm.json")

        // mpm.jsonが存在しない場合はエラー
        if (!configFile.exists()) {
            return "mpm.jsonが存在しません。先に 'mpm init' を実行してください。".left()
        }

        // mpm.jsonを読み込む
        val mpmConfig =
            try {
                val jsonString = configFile.readText()
                Utils.json.decodeFromString<MpmConfig>(jsonString)
            } catch (e: Exception) {
                return "mpm.jsonの読み込みに失敗しました: ${e.message}".left()
            }

        // 管理対象のプラグイン名セット
        val managedPlugins = mpmConfig.plugins.keys

        // pluginsディレクトリからJARファイルを取得
        val pluginsDir = pluginDirectory.getPluginsDirectory()
        val pluginFiles =
            pluginsDir.listFiles { file ->
                file.isFile && file.extension == "jar"
            } ?: emptyArray()

        // localディレクトリを取得（localディレクトリ内のプラグインは削除対象外）
        val localDir = File(rootDir, "local")

        // 削除されたプラグイン名のリスト
        val removedPlugins = mutableListOf<String>()

        // 各JARファイルをチェック
        for (jarFile in pluginFiles) {
            try {
                // localディレクトリ内のファイルはスキップ
                if (jarFile.canonicalPath.startsWith(localDir.canonicalPath)) {
                    continue
                }

                // プラグインデータを取得
                val pluginData = PluginDataUtils.getPluginData(jarFile)
                if (pluginData != null) {
                    val name =
                        when (pluginData) {
                            is party.morino.mpm.api.model.plugin.PluginData.BukkitPluginData -> pluginData.name
                            is party.morino.mpm.api.model.plugin.PluginData.PaperPluginData -> pluginData.name
                        }

                    // 管理対象でない場合は削除
                    if (!managedPlugins.contains(name)) {
                        if (jarFile.delete()) {
                            removedPlugins.add(name)
                        }
                    }
                }
            } catch (e: Exception) {
                // エラーが発生した場合はスキップ
                continue
            }
        }

        return removedPlugins.right()
    }
}