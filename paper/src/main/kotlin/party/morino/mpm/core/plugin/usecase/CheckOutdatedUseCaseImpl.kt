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
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.config.PluginDirectory
import party.morino.mpm.api.config.plugin.MpmConfig
import party.morino.mpm.api.core.plugin.CheckOutdatedUseCase
import party.morino.mpm.api.core.plugin.DownloaderRepository
import party.morino.mpm.api.core.plugin.PluginMetadataManager
import party.morino.mpm.api.core.repository.RepositoryManager
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.api.model.plugin.OutdatedInfo
import party.morino.mpm.api.model.repository.UrlData
import party.morino.mpm.event.PluginOutdatedEvent
import party.morino.mpm.utils.Utils
import java.io.File

/**
 * mpm outdated/outdatedAllコマンドに関するユースケースの実装
 * 依存性はKoinによって注入される
 */
class CheckOutdatedUseCaseImpl :
    CheckOutdatedUseCase,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()
    private val pluginMetadataManager: PluginMetadataManager by inject()
    private val repositorySourceManager: RepositoryManager by inject()
    private val downloaderRepository: DownloaderRepository by inject()
    private val plugin: JavaPlugin by inject()

    /**
     * 指定されたプラグインの更新を確認する
     *
     * @param pluginName プラグイン名
     * @return 成功時は更新情報、失敗時はエラーメッセージ
     */
    override suspend fun checkOutdated(pluginName: String): Either<String, OutdatedInfo> {
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

        // メタデータを読み込む
        val metadata =
            pluginMetadataManager.loadMetadata(pluginName).getOrElse {
                return "プラグインのメタデータが見つかりません: $pluginName".left()
            }

        // リポジトリファイルを取得
        val repositoryFile =
            repositorySourceManager.getRepositoryFile(pluginName)
                ?: return "リポジトリファイルが見つかりません: $pluginName".left()

        // リポジトリ設定から最初のリポジトリを取得
        val firstRepository =
            repositoryFile.repositories.firstOrNull()
                ?: return "リポジトリ設定が見つかりません: $pluginName".left()

        // RepositoryConfigからUrlDataを作成
        val urlData =
            when (firstRepository.type.lowercase()) {
                "github" -> {
                    // GitHub形式: "owner/repository"
                    val parts = firstRepository.repositoryId.split("/")
                    if (parts.size != 2) {
                        return "GitHubリポジトリIDの形式が不正です: ${firstRepository.repositoryId}".left()
                    }
                    UrlData.GithubUrlData(owner = parts[0], repository = parts[1])
                }

                "modrinth" -> {
                    UrlData.ModrinthUrlData(id = firstRepository.repositoryId)
                }

                "spigotmc" -> {
                    UrlData.SpigotMcUrlData(resourceId = firstRepository.repositoryId)
                }

                else -> {
                    return "未対応のリポジトリタイプです: ${firstRepository.type}".left()
                }
            }

        // 最新バージョンを取得
        val latestVersion =
            try {
                downloaderRepository.getLatestVersion(urlData)
            } catch (e: Exception) {
                return "バージョン情報の取得に失敗しました (${firstRepository.type}: ${firstRepository.repositoryId}): ${e.message}"
                    .left()
            }

        // 現在のバージョンと最新バージョンを比較
        val currentVersion = metadata.mpmInfo.version.current.raw
        val needsUpdate = currentVersion != latestVersion.version

        // 更新が必要な場合はイベントを発火
        if (needsUpdate) {
            val outdatedEvent =
                PluginOutdatedEvent(
                    installedPlugin = InstalledPlugin(pluginName),
                    currentVersion = currentVersion,
                    latestVersion = latestVersion.version
                )
            plugin.server.pluginManager.callEvent(outdatedEvent)
        }

        return OutdatedInfo(
            pluginName = pluginName,
            currentVersion = currentVersion,
            latestVersion = latestVersion.version,
            needsUpdate = needsUpdate
        ).right()
    }

    /**
     * すべての管理下プラグインの更新を確認する
     *
     * @return 成功時は更新情報のリスト、失敗時はエラーメッセージ
     */
    override suspend fun checkAllOutdated(): Either<String, List<OutdatedInfo>> {
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

        // すべての管理対象プラグインの更新情報を収集
        val outdatedInfoList = mutableListOf<OutdatedInfo>()

        for (pluginName in mpmConfig.plugins.keys) {
            // "unmanaged"の場合はスキップ
            if (mpmConfig.plugins[pluginName] == "unmanaged") {
                continue
            }

            // 各プラグインの更新情報を取得
            checkOutdated(pluginName).onRight { outdatedInfo ->
                outdatedInfoList.add(outdatedInfo)
            }
        }

        return outdatedInfoList.right()
    }
}