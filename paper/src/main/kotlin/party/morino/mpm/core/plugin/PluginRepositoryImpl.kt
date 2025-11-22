/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.core.plugin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.core.plugin.PluginRepository
import party.morino.mpm.api.model.plugin.PluginData
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * PluginRepositoryの実装クラス
 * プラグインデータの保存と取得を担当
 * 依存性はKoinによって注入される
 */
class PluginRepositoryImpl :
    PluginRepository,
    KoinComponent {
    // Koinによる依存性注入
    private val plugin: JavaPlugin by inject()

    // JSONシリアライザー
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

    // プラグインデータを保存するディレクトリ
    private val pluginsDir = plugin.dataFolder.parentFile

    // プラグインのメタデータを保存するディレクトリ
    private val metadataDir = File(plugin.dataFolder, "metadata")

    init {
        // 必要なディレクトリを作成
        pluginsDir.mkdirs()
        metadataDir.mkdirs()
    }

    /**
     * 指定された名前のプラグインを取得
     * @param name プラグイン名
     * @return プラグインデータ、存在しない場合はnull
     */
    override suspend fun getManagedPluginData(name: String): PluginData? =
        withContext(Dispatchers.IO) {
            val metadataFile = File(metadataDir, "$name.yaml")
            if (!metadataFile.exists()) return@withContext null

            try {
                val content = metadataFile.readText()
                json.decodeFromString<PluginData>(content)
            } catch (e: Exception) {
                plugin.logger.warning("プラグイン $name のメタデータの読み込みに失敗しました: ${e.message}")
                null
            }
        }

    /**
     * すべてのプラグインを取得
     * @return 全プラグインのリスト
     */
    override suspend fun getAllManagedPluginData(): List<PluginData> =
        withContext(Dispatchers.IO) {
            val plugins = mutableListOf<PluginData>()

            metadataDir.listFiles()?.filter { it.extension == "json" }?.forEach { file ->
                try {
                    val content = file.readText()
                    val plugin = json.decodeFromString<PluginData>(content)
                    plugins.add(plugin)
                } catch (e: Exception) {
                    this@PluginRepositoryImpl.plugin.logger.warning(
                        "プラグイン ${file.nameWithoutExtension} のメタデータの読み込みに失敗しました: ${e.message}"
                    )
                }
            }

            plugins
        }

    /**
     * プラグインを保存
     * @param plugin 保存するプラグインデータ
     * @param file プラグインファイル
     */
    override suspend fun savePlugin(
        plugin: PluginData,
        file: File
    ) = withContext(Dispatchers.IO) {
        // プラグイン名の取得
        val pluginName =
            when (plugin) {
                is PluginData.BukkitPluginData -> plugin.name
                is PluginData.PaperPluginData -> plugin.name
            }

        // プラグインファイルをコピー
        val targetFile = File(pluginsDir, "$pluginName.jar")
        Files.copy(file.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)

        // メタデータをJSON形式で保存
        val metadataFile = File(metadataDir, "$pluginName.yaml")
        val jsonContent = json.encodeToString(PluginData.serializer(), plugin)
        metadataFile.writeText(jsonContent)
    }

    /**
     * プラグインを削除
     * @param name 削除するプラグイン名
     * @return 削除に成功した場合はtrue
     */
    override suspend fun removePlugin(name: String): Boolean =
        withContext(Dispatchers.IO) {
            var success = true

            // プラグインファイルの削除
            val pluginFile = File(pluginsDir, "$name.jar")
            if (pluginFile.exists()) {
                success = success && pluginFile.delete()
            }

            // メタデータファイルの削除
            val metadataFile = File(metadataDir, "$name.json")
            if (metadataFile.exists()) {
                success = success && metadataFile.delete()
            }

            success
        }

    /**
     * プラグインの存在確認
     * @param name 確認するプラグイン名
     * @return 存在する場合はtrue
     */
    override suspend fun exists(name: String): Boolean =
        withContext(Dispatchers.IO) {
            val metadataFile = File(metadataDir, "$name.yaml")
            metadataFile.exists()
        }
}