/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.core.plugin

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.serialization.encodeToString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.config.PluginDirectory
import party.morino.mpm.api.config.plugin.MpmConfig
import party.morino.mpm.api.core.plugin.InitUseCase
import party.morino.mpm.utils.PluginDataUtils
import party.morino.mpm.utils.Utils
import java.io.File

/**
 * mpm initコマンドに関するユースケースの実装
 * 依存性はKoinによって注入される
 */
class InitUseCaseImpl :
    InitUseCase,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()

    /**
     * プロジェクトを初期化する
     * rootDirectory/mpm.jsonを生成し、pluginsディレクトリ内のすべてのプラグインをunmanagedとして追加する
     *
     * @param projectName プロジェクト名
     * @param overwrite 既存のmpm.jsonを上書きするかどうか
     * @return 成功時はUnit、失敗時はエラーメッセージ
     */
    override suspend fun initialize(
        projectName: String,
        overwrite: Boolean
    ): Either<String, Unit> {
        // rootディレクトリを取得
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "mpm.json")

        // 既存のmpm.jsonが存在する場合のチェック
        if (configFile.exists() && !overwrite) {
            return "既にmpm.jsonが存在します。上書きする場合は --overwrite フラグを使用してください。".left()
        }

        // pluginsディレクトリからすべてのJARファイルを取得
        val pluginsDir = pluginDirectory.getPluginsDirectory()
        val pluginFiles =
            pluginsDir.listFiles { file ->
                // .jarファイルのみを対象にし、自分自身（MinecraftPluginManager）は除外
                file.isFile && file.extension == "jar"
            } ?: emptyArray()

        // 各JARファイルからプラグイン名を取得してunmanagedマップを作成
        val unmanagedPlugins = mutableMapOf<String, String>()
        pluginFiles.forEach { jarFile ->
            try {
                // JARファイルからプラグイン情報を取得
                val pluginData = PluginDataUtils.getPluginData(jarFile)
                if (pluginData != null) {
                    val pluginName =
                        when (pluginData) {
                            is party.morino.mpm.api.model.plugin.PluginData.BukkitPluginData -> pluginData.name
                            is party.morino.mpm.api.model.plugin.PluginData.PaperPluginData -> pluginData.name
                        }
                    // プラグイン名が空でない場合のみ追加
                    if (pluginName.isNotEmpty()) {
                        unmanagedPlugins[pluginName] = "unmanaged"
                    }
                }
            } catch (e: Exception) {
                // エラーが発生した場合はスキップ（ログは出さない）
                // 無効なJARファイルの可能性がある
            }
        }

        // MpmConfigを作成
        val mpmConfig =
            MpmConfig(
                name = projectName,
                version = "1.0.0",
                plugins = unmanagedPlugins
            )

        // JSONとして保存
        return try {
            val jsonString = Utils.json.encodeToString(mpmConfig)
            configFile.writeText(jsonString)
            Unit.right()
        } catch (e: Exception) {
            "mpm.jsonの作成に失敗しました: ${e.message}".left()
        }
    }
}