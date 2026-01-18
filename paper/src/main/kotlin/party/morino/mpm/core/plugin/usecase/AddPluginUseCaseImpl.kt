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
import party.morino.mpm.api.config.plugin.VersionSpecifier
import party.morino.mpm.api.config.plugin.VersionSpecifierParser
import party.morino.mpm.api.config.plugin.withSortedPlugins
import party.morino.mpm.api.core.plugin.AddPluginUseCase
import party.morino.mpm.api.core.plugin.DownloaderRepository
import party.morino.mpm.api.core.plugin.PluginMetadataManager
import party.morino.mpm.api.core.repository.RepositoryConfig
import party.morino.mpm.api.core.repository.RepositoryManager
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import party.morino.mpm.api.model.repository.UrlData
import party.morino.mpm.event.PluginAddEvent
import party.morino.mpm.utils.Utils
import java.io.File

/**
 * mpm addコマンドに関するユースケースの実装
 * 依存性はKoinによって注入される
 */
class AddPluginUseCaseImpl :
    AddPluginUseCase,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()
    private val repositorySourceManager: RepositoryManager by inject()
    private val downloaderRepository: DownloaderRepository by inject()
    private val metadataManager: PluginMetadataManager by inject()
    private val plugin: JavaPlugin by inject()

    /**
     * プラグインを管理対象に追加する
     * mpm.jsonのpluginsマップにプラグインを追加する
     *
     * @param pluginName プラグイン名
     * @param version バージョン指定（デフォルトはLatest）
     * @return 成功時はUnit、失敗時はエラーメッセージ
     */
    override suspend fun addPlugin(
        pluginName: String,
        version: VersionSpecifier
    ): Either<String, Unit> {
        // rootディレクトリを取得
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "mpm.json")

        // mpm.jsonが存在しない場合はエラー
        if (!configFile.exists()) {
            return "mpm.jsonが存在しません。先に 'mpm init' を実行してください。".left()
        }

        // リポジトリソースからプラグインが存在するか確認
        val repositoryFile =
            repositorySourceManager.getRepositoryFile(pluginName)
                ?: return "リポジトリファイルが見つかりません: $pluginName\n利用可能なリポジトリソースで '$pluginName.json' を検索しましたが見つかりませんでした."
                    .left()

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

        // mpm.jsonを読み込む
        val mpmConfig =
            try {
                val jsonString = configFile.readText()
                Utils.json.decodeFromString<MpmConfig>(jsonString)
            } catch (e: Exception) {
                return "mpm.jsonの読み込みに失敗しました: ${e.message}".left()
            }

        // 既に追加されているか確認（unmanagedの場合は除外）
        if (mpmConfig.plugins.containsKey(pluginName) && mpmConfig.plugins[pluginName] != "unmanaged") {
            return "プラグイン '$pluginName' は既に管理対象に追加されています。".left()
        }

        // VersionSpecifierに応じてバージョンデータを決定
        val versionData: party.morino.mpm.api.model.repository.VersionData =
            when (version) {
                // 最新バージョンを取得
                is VersionSpecifier.Latest -> {
                    try {
                        downloaderRepository.getLatestVersion(urlData)
                    } catch (e: Exception) {
                        return (
                            "バージョン情報の取得に失敗しました " +
                                "(${firstRepository.type}: ${firstRepository.repositoryId}): " +
                                e.message
                        ).left()
                    }
                }
                // 固定バージョンを使用
                is VersionSpecifier.Fixed -> {
                    // 固定バージョンの場合、downloadIdを取得する必要がある
                    // ここでは最新バージョンを取得してdownloadIdを使う
                    // （将来的には指定されたバージョンのdownloadIdを取得する実装が必要）
                    try {
                        val latestVersionData = downloaderRepository.getLatestVersion(urlData)
                        party.morino.mpm.api.model.repository.VersionData(
                            downloadId = latestVersionData.downloadId,
                            version = version.version
                        )
                    } catch (e: Exception) {
                        return (
                            "バージョン情報の取得に失敗しました " +
                                "(${firstRepository.type}: ${firstRepository.repositoryId}): " +
                                e.message
                        ).left()
                    }
                }
                // Tag指定（将来実装予定）
                is VersionSpecifier.Tag -> {
                    return "Tag指定は現在サポートされていません。".left()
                }
                // Pattern指定（将来実装予定）
                is VersionSpecifier.Pattern -> {
                    return "Pattern指定は現在サポートされていません。".left()
                }
                // Sync指定: ターゲットプラグインのバージョンに同期
                is VersionSpecifier.Sync -> {
                    // ターゲットプラグインがmpm.jsonに存在するか確認
                    val targetVersion =
                        mpmConfig.plugins[version.targetPlugin]
                            ?: return "同期対象のプラグイン '${version.targetPlugin}' がmpm.jsonに存在しません。".left()

                    // ターゲットがunmanagedの場合はエラー
                    if (targetVersion == "unmanaged") {
                        return "同期対象のプラグイン '${version.targetPlugin}' は手動管理（unmanaged）です。".left()
                    }

                    // ターゲットもSync指定の場合はエラー
                    if (VersionSpecifierParser.isSyncFormat(targetVersion)) {
                        return "同期対象のプラグイン '${version.targetPlugin}' も同期設定になっています。".left()
                    }

                    // ターゲットのバージョンを解決（latestの場合はターゲット側のリポジトリから取得）
                    val resolvedVersion =
                        if (targetVersion == "latest") {
                            metadataManager.loadMetadata(version.targetPlugin).fold(
                                {
                                    // メタデータがない場合はターゲットのリポジトリから最新バージョンを取得
                                    val targetRepo =
                                        repositorySourceManager
                                            .getRepositoryFile(version.targetPlugin)
                                            ?.repositories
                                            ?.firstOrNull()
                                            ?: return "ターゲットプラグインのリポジトリが見つかりません: ${version.targetPlugin}".left()
                                    val targetUrlData =
                                        createUrlData(targetRepo)
                                            ?: return "未対応のリポジトリタイプです: ${targetRepo.type}".left()
                                    try {
                                        downloaderRepository.getLatestVersion(targetUrlData).version
                                    } catch (e: Exception) {
                                        return "ターゲットプラグインのバージョン取得に失敗しました: ${e.message}".left()
                                    }
                                },
                                { it.mpmInfo.version.current.raw }
                            )
                        } else {
                            targetVersion
                        }

                    // アドオン側で解決されたバージョンに対応するダウンロード情報を取得
                    try {
                        downloaderRepository.getVersionByName(urlData, resolvedVersion)
                    } catch (e: Exception) {
                        return "バージョン '$resolvedVersion' の取得に失敗しました: ${e.message}".left()
                    }
                }
            }
        val metadata =
            metadataManager
                .createMetadata(pluginName, firstRepository, versionData, "add")
                .getOrElse { return it.left() }

        // PluginAddEventを発火して、他のプラグインがキャンセルできるようにする
        val addEvent =
            PluginAddEvent(
                repositoryPlugin = RepositoryPlugin(pluginName),
                versionSpecifier = version,
                repositoryType = firstRepository.type,
                repositoryId = firstRepository.repositoryId
            )
        plugin.server.pluginManager.callEvent(addEvent)

        // イベントがキャンセルされた場合はスキップ
        if (addEvent.isCancelled) {
            return "追加がキャンセルされました".left()
        }

        // pluginsマップに追加（Syncの場合は"sync:PluginName"形式で保存）
        val updatedPlugins = mpmConfig.plugins.toMutableMap()
        val versionToSave =
            when (version) {
                is VersionSpecifier.Sync -> VersionSpecifierParser.toVersionString(version)
                is VersionSpecifier.Latest -> "latest"
                else -> versionData.version
            }
        updatedPlugins[pluginName] = versionToSave

        // 更新されたMpmConfigを作成し、pluginsをa-Z順にソート
        val updatedConfig = mpmConfig.copy(plugins = updatedPlugins).withSortedPlugins()

        // mpm.jsonを保存
        try {
            val jsonString = Utils.json.encodeToString(updatedConfig)
            configFile.writeText(jsonString)
        } catch (e: Exception) {
            return "mpm.jsonの更新に失敗しました: ${e.message}".left()
        }

        // mpm.jsonの保存が成功した後にメタデータを保存
        metadataManager.saveMetadata(pluginName, metadata).getOrElse { return it.left() }

        return Unit.right()
    }

    /**
     * RepositoryConfigからUrlDataを生成する
     * @return 変換されたUrlData、未対応のタイプの場合はnull
     */
    private fun createUrlData(repo: RepositoryConfig): UrlData? =
        when (repo.type.lowercase()) {
            "github" ->
                repo.repositoryId
                    .split("/")
                    .takeIf { it.size == 2 }
                    ?.let { (owner, repository) -> UrlData.GithubUrlData(owner, repository) }
            "modrinth" -> UrlData.ModrinthUrlData(repo.repositoryId)
            "spigotmc" -> UrlData.SpigotMcUrlData(repo.repositoryId)
            else -> null
        }
}