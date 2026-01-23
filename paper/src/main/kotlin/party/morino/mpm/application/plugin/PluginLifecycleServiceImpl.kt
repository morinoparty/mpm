/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
 * and related and neighboring rights to this software to the public domain worldwide.
 * This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.application.model.InstallResult
import party.morino.mpm.api.application.model.PluginInstallInfo
import party.morino.mpm.api.application.model.PluginRemovalInfo
import party.morino.mpm.api.application.plugin.PluginLifecycleService
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.downloader.DownloaderRepository
import party.morino.mpm.api.domain.downloader.model.UrlData
import party.morino.mpm.api.domain.downloader.model.VersionData
import party.morino.mpm.api.domain.plugin.model.ManagedPlugin
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.domain.plugin.model.VersionSpecifierParser
import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import party.morino.mpm.api.domain.project.dto.MpmConfig
import party.morino.mpm.api.domain.project.dto.withSortedPlugins
import party.morino.mpm.api.domain.repository.RepositoryConfig
import party.morino.mpm.api.domain.repository.RepositoryManager
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.api.model.plugin.PluginData
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import party.morino.mpm.api.shared.error.MpmError
import party.morino.mpm.event.PluginAddEvent
import party.morino.mpm.event.PluginInstallEvent
import party.morino.mpm.event.PluginRemoveEvent
import party.morino.mpm.event.PluginUninstallEvent
import party.morino.mpm.utils.DataClassReplacer.replaceTemplate
import party.morino.mpm.utils.PluginDataUtils
import party.morino.mpm.utils.Utils
import java.io.File
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier as LegacyVersionSpecifier

/**
 * プラグインのライフサイクル管理を行うApplication Service実装
 *
 * UseCase のロジックを統合した実装
 */
class PluginLifecycleServiceImpl :
    PluginLifecycleService,
    KoinComponent {
    // 直接依存する infrastructure/domain コンポーネント
    private val pluginDirectory: PluginDirectory by inject()
    private val repositoryManager: RepositoryManager by inject()
    private val downloaderRepository: DownloaderRepository by inject()
    private val metadataManager: PluginMetadataManager by inject()
    private val plugin: JavaPlugin by inject()

    /**
     * プラグインを管理対象に追加する
     *
     * AddPluginUseCaseImpl から移行したロジック
     */
    override suspend fun add(
        name: PluginName,
        version: VersionSpecifier
    ): Either<MpmError, ManagedPlugin> {
        val pluginName = name.value
        val legacyVersion = toLegacyVersionSpecifier(version)

        // rootディレクトリを取得
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "mpm.json")

        // mpm.jsonが存在しない場合はエラー
        if (!configFile.exists()) {
            return MpmError.ProjectError.NotInitialized.left()
        }

        // リポジトリソースからプラグインが存在するか確認
        val repositoryFile =
            repositoryManager.getRepositoryFile(pluginName)
                ?: return MpmError.DownloadError.RepositoryNotFound("unknown", pluginName).left()

        // リポジトリ設定から最初のリポジトリを取得
        val firstRepository =
            repositoryFile.repositories.firstOrNull()
                ?: return MpmError.DownloadError.RepositoryNotFound("config", pluginName).left()

        // RepositoryConfigからUrlDataを作成
        val urlData =
            createUrlData(firstRepository)
                ?: return MpmError.DownloadError.RepositoryNotFound(firstRepository.type, pluginName).left()

        // mpm.jsonを読み込む
        val mpmConfig =
            try {
                val jsonString = configFile.readText()
                Utils.json.decodeFromString<MpmConfig>(jsonString)
            } catch (e: Exception) {
                return MpmError.ProjectError.ConfigParseError(e.message ?: "Unknown error").left()
            }

        // 既に追加されているか確認（unmanagedの場合は除外）
        if (mpmConfig.plugins.containsKey(pluginName) && mpmConfig.plugins[pluginName] != "unmanaged") {
            return MpmError.PluginError.AlreadyExists(pluginName).left()
        }

        // VersionSpecifierに応じてバージョンデータを決定
        val versionData: VersionData =
            resolveVersionData(
                legacyVersion,
                urlData,
                firstRepository,
                mpmConfig,
                pluginName
            ).getOrElse { return it.left() }

        // メタデータを作成
        val metadata =
            metadataManager
                .createMetadata(pluginName, firstRepository, versionData, "add")
                .getOrElse { return MpmError.PluginError.AddFailed(pluginName, it).left() }

        // PluginAddEventを発火して、他のプラグインがキャンセルできるようにする
        val addEvent =
            PluginAddEvent(
                repositoryPlugin = RepositoryPlugin(pluginName),
                versionSpecifier = legacyVersion,
                repositoryType = firstRepository.type,
                repositoryId = firstRepository.repositoryId
            )
        plugin.server.pluginManager.callEvent(addEvent)

        // イベントがキャンセルされた場合はスキップ
        if (addEvent.isCancelled) {
            return MpmError.PluginError.AddFailed(pluginName, "Cancelled by event").left()
        }

        // pluginsマップに追加
        val updatedPlugins = mpmConfig.plugins.toMutableMap()
        val versionToSave =
            when (legacyVersion) {
                is LegacyVersionSpecifier.Sync -> VersionSpecifierParser.toVersionString(legacyVersion)
                is LegacyVersionSpecifier.Latest -> "latest"
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
            return MpmError.PluginError.AddFailed(pluginName, "Failed to save mpm.json: ${e.message}").left()
        }

        // mpm.jsonの保存が成功した後にメタデータを保存
        metadataManager
            .saveMetadata(pluginName, metadata)
            .getOrElse { return MpmError.PluginError.AddFailed(pluginName, it).left() }

        // ManagedPluginを返す（メタデータから構築）
        return ManagedPlugin.fromDto(metadata).right()
    }

    /**
     * プラグインを管理対象から削除する
     *
     * RemovePluginUseCaseImpl から移行したロジック
     */
    override suspend fun remove(name: PluginName): Either<MpmError, Unit> {
        val pluginName = name.value

        // rootディレクトリを取得
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "mpm.json")

        // mpm.jsonが存在しない場合はエラー
        if (!configFile.exists()) {
            return MpmError.ProjectError.NotInitialized.left()
        }

        // mpm.jsonを読み込む
        val mpmConfig =
            try {
                val jsonString = configFile.readText()
                Utils.json.decodeFromString<MpmConfig>(jsonString)
            } catch (e: Exception) {
                return MpmError.ProjectError.ConfigParseError(e.message ?: "Unknown error").left()
            }

        // プラグインが管理対象に含まれているか確認
        if (!mpmConfig.plugins.containsKey(pluginName)) {
            return MpmError.PluginError.NotFound(pluginName).left()
        }

        // PluginRemoveEventを発火して、他のプラグインがキャンセルできるようにする
        val removeEvent =
            PluginRemoveEvent(
                installedPlugin = InstalledPlugin(pluginName)
            )
        plugin.server.pluginManager.callEvent(removeEvent)

        // イベントがキャンセルされた場合はエラー
        if (removeEvent.isCancelled) {
            return MpmError.PluginError.RemoveFailed(pluginName, "Cancelled by event").left()
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
            MpmError.PluginError.RemoveFailed(pluginName, "Failed to save mpm.json: ${e.message}").left()
        }
    }

    /**
     * プラグインをインストールする
     *
     * PluginInstallUseCaseImplから移行したロジック
     */
    override suspend fun install(name: PluginName): Either<MpmError, InstallResult> {
        val pluginName = name.value

        // メタデータを読み込む
        val metadata =
            metadataManager.loadMetadata(pluginName).getOrElse {
                return MpmError.PluginError.MetadataNotFound(pluginName).left()
            }

        val mpmInfo = metadata.mpmInfo
        val pluginInfo = metadata.pluginInfo
        val repositoryInfo = mpmInfo.repository

        // リポジトリタイプからUrlDataを生成
        val urlData =
            when (repositoryInfo.type.name.lowercase()) {
                "github" -> {
                    val parts = repositoryInfo.id.split("/")
                    if (parts.size != 2) {
                        return MpmError.PluginError
                            .InstallFailed(
                                pluginName,
                                "Invalid GitHub repository ID format: ${repositoryInfo.id}"
                            ).left()
                    }
                    UrlData.GithubUrlData(owner = parts[0], repository = parts[1])
                }
                "modrinth" -> UrlData.ModrinthUrlData(id = repositoryInfo.id)
                "spigotmc" -> UrlData.SpigotMcUrlData(resourceId = repositoryInfo.id)
                else -> return MpmError.PluginError.UnsupportedRepository(repositoryInfo.type.name).left()
            }

        // 最新バージョン情報を取得
        val latestVersionData =
            try {
                downloaderRepository.getLatestVersion(urlData)
            } catch (e: Exception) {
                return MpmError.PluginError
                    .VersionResolutionFailed(
                        pluginName,
                        "Failed to get latest version: ${e.message}"
                    ).left()
            }

        // メタデータからバージョン情報を作成
        val versionData = VersionData(mpmInfo.download.downloadId, mpmInfo.version.current.raw)

        // メタデータを更新（最新バージョン情報を反映）
        val updatedMetadataWithLatest =
            metadataManager
                .updateMetadata(pluginName, versionData, latestVersionData, "install")
                .getOrElse {
                    return MpmError.PluginError.MetadataSaveFailed(pluginName, it).left()
                }

        // PluginInstallEventを発火して、他のプラグインがキャンセルできるようにする
        val installEvent =
            PluginInstallEvent(
                repositoryPlugin = RepositoryPlugin(pluginName),
                version = versionData.version,
                repositoryType = repositoryInfo.type.name,
                repositoryId = repositoryInfo.id
            )
        plugin.server.pluginManager.callEvent(installEvent)

        // イベントがキャンセルされた場合はエラー
        if (installEvent.isCancelled) {
            return MpmError.PluginError.OperationCancelled(pluginName, "install").left()
        }

        // プラグインをダウンロード
        val downloadedFile =
            try {
                downloaderRepository.downloadByVersion(
                    urlData,
                    versionData,
                    mpmInfo.fileNamePattern
                )
            } catch (e: Exception) {
                return MpmError.PluginError
                    .InstallFailed(
                        pluginName,
                        "Failed to download: ${e.message}"
                    ).left()
            }

        if (downloadedFile == null) {
            return MpmError.PluginError
                .InstallFailed(
                    pluginName,
                    "Download returned null"
                ).left()
        }

        // ファイル名を生成
        val template = mpmInfo.fileNameTemplate ?: "<pluginInfo.name>-<mpmInfo.version.current.normalized>.jar"
        val newFileName = generateFileName(template, pluginInfo.name, mpmInfo.version.current.normalized)

        // 古いファイルを削除（存在する場合）
        val oldFileName = mpmInfo.download.fileName
        var removedInfo: PluginRemovalInfo? = null
        if (oldFileName != null && oldFileName != newFileName) {
            val pluginsDir = pluginDirectory.getPluginsDirectory()
            val oldFile = File(pluginsDir, oldFileName)
            if (oldFile.exists()) {
                oldFile.delete()
                removedInfo =
                    PluginRemovalInfo(
                        name = pluginName,
                        version = mpmInfo.version.current.normalized
                    )
            }
        }

        // ダウンロードしたファイルをpluginsディレクトリに移動
        val pluginsDir = pluginDirectory.getPluginsDirectory()
        val targetFile = File(pluginsDir, newFileName)
        try {
            downloadedFile.copyTo(targetFile, overwrite = true)
            downloadedFile.delete()
        } catch (e: Exception) {
            return MpmError.PluginError
                .InstallFailed(
                    pluginName,
                    "Failed to move file: ${e.message}"
                ).left()
        }

        // ファイル名をメタデータに記録して保存
        val updatedMetadata =
            updatedMetadataWithLatest.copy(
                mpmInfo =
                    updatedMetadataWithLatest.mpmInfo.copy(
                        download =
                            updatedMetadataWithLatest.mpmInfo.download.copy(
                                fileName = newFileName
                            )
                    )
            )
        metadataManager.saveMetadata(pluginName, updatedMetadata).getOrElse {
            return MpmError.PluginError.MetadataSaveFailed(pluginName, it).left()
        }

        // インストール結果を返す
        return InstallResult(
            installed =
                PluginInstallInfo(
                    name = pluginName,
                    currentVersion = updatedMetadata.mpmInfo.version.current.raw,
                    latestVersion = updatedMetadata.mpmInfo.version.latest.raw
                ),
            removed = removedInfo
        ).right()
    }

    /**
     * プラグインをアンインストールする
     *
     * UninstallPluginUseCaseImplから移行したロジック
     * mpm.jsonから削除し、pluginsディレクトリからJARファイルも削除する
     */
    override suspend fun uninstall(name: PluginName): Either<MpmError, Unit> {
        val pluginName = name.value

        // rootディレクトリを取得
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "mpm.json")

        // mpm.jsonが存在しない場合はエラー
        if (!configFile.exists()) {
            return MpmError.ProjectError.NotInitialized.left()
        }

        // mpm.jsonを読み込む
        val mpmConfig =
            try {
                val jsonString = configFile.readText()
                Utils.json.decodeFromString<MpmConfig>(jsonString)
            } catch (e: Exception) {
                return MpmError.ProjectError.ConfigParseError(e.message ?: "Unknown error").left()
            }

        // プラグインが管理対象に含まれているか確認
        if (!mpmConfig.plugins.containsKey(pluginName)) {
            return MpmError.PluginError.NotFound(pluginName).left()
        }

        // pluginsディレクトリからJARファイルを探す
        val pluginsDir = pluginDirectory.getPluginsDirectory()
        val pluginFiles =
            pluginsDir.listFiles { file ->
                file.isFile && file.extension == "jar"
            } ?: emptyArray()

        // 対象のJARファイルを特定
        var targetJarFile: File? = null
        for (jarFile in pluginFiles) {
            try {
                val pluginData = PluginDataUtils.getPluginData(jarFile)
                if (pluginData != null) {
                    val jarPluginName =
                        when (pluginData) {
                            is PluginData.BukkitPluginData -> pluginData.name
                            is PluginData.PaperPluginData -> pluginData.name
                        }
                    if (jarPluginName == pluginName) {
                        targetJarFile = jarFile
                        break
                    }
                }
            } catch (e: Exception) {
                // エラーが発生した場合はスキップ
                continue
            }
        }

        // PluginUninstallEventを発火して、他のプラグインがキャンセルできるようにする
        val uninstallEvent =
            PluginUninstallEvent(
                installedPlugin = InstalledPlugin(pluginName),
                jarFile = targetJarFile
            )
        plugin.server.pluginManager.callEvent(uninstallEvent)

        // イベントがキャンセルされた場合はエラー
        if (uninstallEvent.isCancelled) {
            return MpmError.PluginError.OperationCancelled(pluginName, "uninstall").left()
        }

        // JARファイルを削除
        targetJarFile?.delete()

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
            MpmError.PluginError
                .UninstallFailed(
                    pluginName,
                    "Failed to save mpm.json: ${e.message}"
                ).left()
        }
    }

    /**
     * 管理されていないプラグインを削除する
     *
     * RemoveUnmanagedUseCaseImplから移行したロジック
     * mpm.jsonに含まれていないプラグインのJARファイルを削除する
     */
    override suspend fun removeUnmanaged(): Either<MpmError, Int> {
        // rootディレクトリを取得
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "mpm.json")

        // mpm.jsonが存在しない場合はエラー
        if (!configFile.exists()) {
            return MpmError.ProjectError.NotInitialized.left()
        }

        // mpm.jsonを読み込む
        val mpmConfig =
            try {
                val jsonString = configFile.readText()
                Utils.json.decodeFromString<MpmConfig>(jsonString)
            } catch (e: Exception) {
                return MpmError.ProjectError.ConfigParseError(e.message ?: "Unknown error").left()
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

        // 削除されたプラグイン数
        var removedCount = 0

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
                    val jarPluginName =
                        when (pluginData) {
                            is PluginData.BukkitPluginData -> pluginData.name
                            is PluginData.PaperPluginData -> pluginData.name
                        }

                    // 管理対象でない場合は削除
                    if (!managedPlugins.contains(jarPluginName)) {
                        if (jarFile.delete()) {
                            removedCount++
                        }
                    }
                }
            } catch (e: Exception) {
                // エラーが発生した場合はスキップ
                continue
            }
        }

        return removedCount.right()
    }

    // ===== Private Helper Methods =====

    /**
     * VersionSpecifierに応じてバージョンデータを解決する
     */
    private suspend fun resolveVersionData(
        version: LegacyVersionSpecifier,
        urlData: UrlData,
        firstRepository: RepositoryConfig,
        mpmConfig: MpmConfig,
        pluginName: String
    ): Either<MpmError, VersionData> =
        when (version) {
            is LegacyVersionSpecifier.Latest -> {
                try {
                    downloaderRepository.getLatestVersion(urlData).right()
                } catch (e: Exception) {
                    MpmError.PluginError.VersionResolutionFailed(pluginName, e.message ?: "Unknown error").left()
                }
            }
            is LegacyVersionSpecifier.Fixed -> {
                try {
                    val latestVersionData = downloaderRepository.getLatestVersion(urlData)
                    VersionData(
                        downloadId = latestVersionData.downloadId,
                        version = version.version
                    ).right()
                } catch (e: Exception) {
                    MpmError.PluginError.VersionResolutionFailed(pluginName, e.message ?: "Unknown error").left()
                }
            }
            is LegacyVersionSpecifier.Tag -> {
                MpmError.PluginError.VersionResolutionFailed(pluginName, "Tag is not supported").left()
            }
            is LegacyVersionSpecifier.Pattern -> {
                MpmError.PluginError.VersionResolutionFailed(pluginName, "Pattern is not supported").left()
            }
            is LegacyVersionSpecifier.Sync -> {
                resolveSyncVersion(version, urlData, mpmConfig, pluginName)
            }
        }

    /**
     * Sync バージョンを解決する
     */
    private suspend fun resolveSyncVersion(
        version: LegacyVersionSpecifier.Sync,
        urlData: UrlData,
        mpmConfig: MpmConfig,
        pluginName: String
    ): Either<MpmError, VersionData> {
        // ターゲットプラグインがmpm.jsonに存在するか確認
        val targetVersion =
            mpmConfig.plugins[version.targetPlugin]
                ?: return MpmError.PluginError
                    .VersionResolutionFailed(
                        pluginName,
                        "Sync target '${version.targetPlugin}' not found"
                    ).left()

        // ターゲットがunmanagedの場合はエラー
        if (targetVersion == "unmanaged") {
            return MpmError.PluginError
                .VersionResolutionFailed(
                    pluginName,
                    "Sync target '${version.targetPlugin}' is unmanaged"
                ).left()
        }

        // ターゲットもSync指定の場合はエラー
        if (VersionSpecifierParser.isSyncFormat(targetVersion)) {
            return MpmError.PluginError
                .VersionResolutionFailed(
                    pluginName,
                    "Sync target '${version.targetPlugin}' is also sync"
                ).left()
        }

        // ターゲットのバージョンを解決
        val resolvedVersion =
            if (targetVersion == "latest") {
                metadataManager.loadMetadata(version.targetPlugin).fold(
                    {
                        // メタデータがない場合はターゲットのリポジトリから最新バージョンを取得
                        val targetRepo =
                            repositoryManager
                                .getRepositoryFile(version.targetPlugin)
                                ?.repositories
                                ?.firstOrNull()
                                ?: return MpmError.PluginError
                                    .VersionResolutionFailed(
                                        pluginName,
                                        "Target repository not found"
                                    ).left()
                        val targetUrlData =
                            createUrlData(targetRepo)
                                ?: return MpmError.PluginError
                                    .VersionResolutionFailed(
                                        pluginName,
                                        "Unsupported repository type"
                                    ).left()
                        try {
                            downloaderRepository.getLatestVersion(targetUrlData).version
                        } catch (e: Exception) {
                            return MpmError.PluginError
                                .VersionResolutionFailed(
                                    pluginName,
                                    e.message ?: "Unknown error"
                                ).left()
                        }
                    },
                    { it.mpmInfo.version.current.raw }
                )
            } else {
                targetVersion
            }

        // アドオン側で解決されたバージョンに対応するダウンロード情報を取得
        return try {
            downloaderRepository.getVersionByName(urlData, resolvedVersion).right()
        } catch (e: Exception) {
            MpmError.PluginError
                .VersionResolutionFailed(
                    pluginName,
                    "Version '$resolvedVersion' not found: ${e.message}"
                ).left()
        }
    }

    /**
     * RepositoryConfigからUrlDataを生成する
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

    /**
     * 新しいVersionSpecifierを旧APIのVersionSpecifierに変換する
     */
    private fun toLegacyVersionSpecifier(specifier: VersionSpecifier): LegacyVersionSpecifier =
        when (specifier) {
            is VersionSpecifier.Latest -> LegacyVersionSpecifier.Latest
            is VersionSpecifier.Fixed -> LegacyVersionSpecifier.Fixed(specifier.version)
            is VersionSpecifier.Tag -> LegacyVersionSpecifier.Tag(specifier.tag)
            is VersionSpecifier.Pattern -> LegacyVersionSpecifier.Pattern(specifier.pattern)
            is VersionSpecifier.Sync -> LegacyVersionSpecifier.Sync(specifier.targetPlugin)
        }

    /**
     * テンプレートからファイル名を生成する
     */
    private fun generateFileName(
        template: String,
        pluginName: String,
        versionString: String
    ): String {
        // テンプレート置換用のデータクラス
        data class PluginInfo(
            val name: String
        )

        data class CurrentVersion(
            val normalized: String
        )

        data class MpmInfoVersion(
            val current: CurrentVersion
        )

        data class MpmInfo(
            val version: MpmInfoVersion
        )

        data class FileNameData(
            val pluginInfo: PluginInfo,
            val mpmInfo: MpmInfo
        )

        val data =
            FileNameData(
                pluginInfo = PluginInfo(name = pluginName),
                mpmInfo = MpmInfo(version = MpmInfoVersion(current = CurrentVersion(normalized = versionString)))
            )

        return template.replaceTemplate(data)
    }
}