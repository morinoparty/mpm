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
import party.morino.mpm.api.config.plugin.withSortedPlugins
import party.morino.mpm.api.core.plugin.UninstallPluginUseCase
import party.morino.mpm.utils.PluginDataUtils
import party.morino.mpm.utils.Utils
import java.io.File

/**
 * mpm uninstallコマンドに関するユースケースの実装
 * 依存性はKoinによって注入される
 */
class UninstallPluginUseCaseImpl :
    UninstallPluginUseCase,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()

    /**
     * プラグインをアンインストールする
     * mpm.jsonから削除し、pluginsディレクトリからJARファイルも削除する
     *
     * @param pluginName プラグイン名
     * @return 成功時はUnit、失敗時はエラーメッセージ
     */
    override suspend fun uninstallPlugin(pluginName: String): Either<String, Unit> {
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

        // プラグインが管理対象に含まれているか確認
        if (!mpmConfig.plugins.containsKey(pluginName)) {
            return "プラグイン '$pluginName' は管理対象に含まれていません。".left()
        }

        // pluginsディレクトリからJARファイルを探して削除
        val pluginsDir = pluginDirectory.getPluginsDirectory()
        val pluginFiles =
            pluginsDir.listFiles { file ->
                file.isFile && file.extension == "jar"
            } ?: emptyArray()

        var fileDeleted = false
        for (jarFile in pluginFiles) {
            try {
                val pluginData = PluginDataUtils.getPluginData(jarFile)
                if (pluginData != null) {
                    val name =
                        when (pluginData) {
                            is party.morino.mpm.api.model.plugin.PluginData.BukkitPluginData -> pluginData.name
                            is party.morino.mpm.api.model.plugin.PluginData.PaperPluginData -> pluginData.name
                        }

                    if (name == pluginName) {
                        // ファイルを削除
                        if (jarFile.delete()) {
                            fileDeleted = true
                        }
                        break
                    }
                }
            } catch (e: Exception) {
                // エラーが発生した場合はスキップ
                continue
            }
        }

        // mpm.jsonからプラグインを削除
        val updatedPlugins = mpmConfig.plugins.toMutableMap()
        updatedPlugins.remove(pluginName)

        // 更新されたMpmConfigを作成し、pluginsをa-Z順にソート
        val updatedConfig = mpmConfig.copy(plugins = updatedPlugins).withSortedPlugins()

        // JSONとして保存
        return try {
            val jsonString = Utils.json.encodeToString(updatedConfig)
            configFile.writeText(jsonString)
            Unit.right()
        } catch (e: Exception) {
            "mpm.jsonの更新に失敗しました: ${e.message}".left()
        }
    }
}