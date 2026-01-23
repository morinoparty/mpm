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
import com.charleskorn.kaml.Yaml
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.application.model.BulkInstallResult
import party.morino.mpm.api.application.model.InstallResult
import party.morino.mpm.api.application.model.PluginInstallInfo
import party.morino.mpm.api.application.model.PluginRemovalInfo
import party.morino.mpm.api.application.model.UpdateResult
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.application.plugin.PluginUpdateService
import party.morino.mpm.api.domain.backup.ServerBackupManager
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.downloader.DownloaderRepository
import party.morino.mpm.api.domain.downloader.model.UrlData
import party.morino.mpm.api.domain.downloader.model.VersionData
import party.morino.mpm.api.domain.plugin.dto.ManagedPluginDto
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.domain.plugin.model.VersionSpecifierParser
import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import party.morino.mpm.api.domain.project.dto.MpmConfig
import party.morino.mpm.api.domain.project.dto.getPluginsSyncingTo
import party.morino.mpm.api.domain.project.dto.topologicalSortPlugins
import party.morino.mpm.api.domain.project.dto.validateSyncDependencies
import party.morino.mpm.api.domain.repository.RepositoryManager
import party.morino.mpm.api.model.backup.BackupReason
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import party.morino.mpm.api.shared.error.MpmError
import party.morino.mpm.event.PluginInstallEvent
import party.morino.mpm.event.PluginLockEvent
import party.morino.mpm.event.PluginUnlockEvent
import party.morino.mpm.event.PluginUpdateEvent
import party.morino.mpm.utils.DataClassReplacer.replaceTemplate
import party.morino.mpm.utils.Utils
import java.io.File

/**
 * プラグインの更新を行うApplication Service実装
 *
 * UseCaseのロジックを直接実装
 */
class PluginUpdateServiceImpl :
    PluginUpdateService,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()
    private val pluginMetadataManager: PluginMetadataManager by inject()
    private val repositoryManager: RepositoryManager by inject()
    private val downloaderRepository: DownloaderRepository by inject()
    private val backupManager: ServerBackupManager by inject()
    private val infoService: PluginInfoService by inject()
    private val plugin: JavaPlugin by inject()

    /**
     * 更新可能なすべてのプラグインを更新する
     *
     * UpdatePluginUseCaseImplから移行したロジック
     */
    override suspend fun update(): Either<MpmError, List<UpdateResult>> {
        // すべてのプラグインの更新情報を取得
        val outdatedInfoList =
            infoService.checkAllOutdated().getOrElse {
                return it.left()
            }

        // 更新が必要なプラグインがある場合、バックアップを作成
        val hasUpdates = outdatedInfoList.any { it.needsUpdate }
        if (hasUpdates) {
            backupManager.createBackup(BackupReason.UPDATE).fold(
                { plugin.logger.warning("バックアップの作成に失敗しました: $it") },
                { plugin.logger.info("バックアップを作成しました: ${it.fileName}") }
            )
        }

        // mpm.jsonを読み込んでSync依存関係を取得
        val mpmConfig = loadMpmConfig()

        // 更新結果のリスト
        val updateResults = mutableListOf<UpdateResult>()

        // 更新が成功したプラグイン名を追跡（Syncプラグインの連動更新用）
        val updatedPlugins = mutableSetOf<String>()

        // 更新が必要なプラグインを処理
        for (outdatedInfo in outdatedInfoList) {
            // 更新が不要な場合はスキップ
            if (!outdatedInfo.needsUpdate) {
                continue
            }

            // メタデータを読み込んでロック状態を確認
            val metadata = pluginMetadataManager.loadMetadata(outdatedInfo.pluginName).getOrNull()
            if (metadata == null) {
                updateResults.add(
                    UpdateResult(
                        pluginName = outdatedInfo.pluginName,
                        oldVersion = outdatedInfo.currentVersion,
                        newVersion = outdatedInfo.latestVersion,
                        success = false,
                        errorMessage = "メタデータの読み込みに失敗しました"
                    )
                )
                continue
            }

            // ロックされている場合はスキップ
            if (metadata.mpmInfo.settings.lock == true) {
                updateResults.add(
                    UpdateResult(
                        pluginName = outdatedInfo.pluginName,
                        oldVersion = outdatedInfo.currentVersion,
                        newVersion = outdatedInfo.latestVersion,
                        success = false,
                        errorMessage = "プラグインがロックされています"
                    )
                )
                continue
            }

            // PluginUpdateEventを発火して、他のプラグインがキャンセルできるようにする
            val updateEvent =
                PluginUpdateEvent(
                    installedPlugin = InstalledPlugin(outdatedInfo.pluginName),
                    beforeVersion = VersionSpecifier.Fixed(outdatedInfo.currentVersion),
                    targetVersion = VersionSpecifier.Fixed(outdatedInfo.latestVersion)
                )
            plugin.server.pluginManager.callEvent(updateEvent)

            // イベントがキャンセルされた場合はスキップ
            if (updateEvent.isCancelled) {
                updateResults.add(
                    UpdateResult(
                        pluginName = outdatedInfo.pluginName,
                        oldVersion = outdatedInfo.currentVersion,
                        newVersion = outdatedInfo.latestVersion,
                        success = false,
                        errorMessage = "更新がキャンセルされました"
                    )
                )
                continue
            }

            // プラグインをインストール（既存のファイルは上書きされる）
            val installResult = installSinglePlugin(outdatedInfo.pluginName)

            installResult.fold(
                // インストール失敗時
                { errorMessage ->
                    updateResults.add(
                        UpdateResult(
                            pluginName = outdatedInfo.pluginName,
                            oldVersion = outdatedInfo.currentVersion,
                            newVersion = outdatedInfo.latestVersion,
                            success = false,
                            errorMessage = errorMessage
                        )
                    )
                },
                // インストール成功時
                {
                    updateResults.add(
                        UpdateResult(
                            pluginName = outdatedInfo.pluginName,
                            oldVersion = outdatedInfo.currentVersion,
                            newVersion = outdatedInfo.latestVersion,
                            success = true
                        )
                    )
                    // 更新成功したプラグインを記録
                    updatedPlugins.add(outdatedInfo.pluginName)
                }
            )
        }

        // Syncプラグインの連動更新
        mpmConfig?.let { config ->
            updateSyncPlugins(config, updatedPlugins, updateResults)
        }

        return updateResults.right()
    }

    /**
     * 指定プラグインを更新する
     */
    override suspend fun update(name: PluginName): Either<MpmError, UpdateResult> {
        // プラグインをインストール
        return installSinglePlugin(name.value).fold(
            { error -> MpmError.PluginError.UpdateFailed(name.value, error).left() },
            {
                UpdateResult(
                    pluginName = name.value,
                    oldVersion = "unknown",
                    newVersion = "latest",
                    success = true,
                    errorMessage = null
                ).right()
            }
        )
    }

    /**
     * mpm.jsonに記載されているすべてのプラグインをインストールする
     *
     * BulkInstallUseCaseImplから移行したロジック
     */
    override suspend fun installAll(): Either<MpmError, BulkInstallResult> {
        // mpm.jsonを読み込む
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "mpm.json")

        if (!configFile.exists()) {
            return MpmError.ProjectError.ConfigNotFound.left()
        }

        val mpmConfig =
            try {
                val jsonString = configFile.readText()
                Utils.json.decodeFromString<MpmConfig>(jsonString)
            } catch (e: Exception) {
                return MpmError.ProjectError.ConfigParseError(e.message ?: "不明なエラー").left()
            }

        // Sync依存関係のバリデーション
        mpmConfig.validateSyncDependencies().onLeft { error ->
            return MpmError.ProjectError.SyncValidationFailed(error.toMessage()).left()
        }

        // トポロジカルソートでプラグインを並べ替え（依存先が先に来るように）
        val sortedPlugins = mpmConfig.topologicalSortPlugins()

        // 解決済みバージョンを追跡（Syncプラグインのバージョン解決に使用）
        val resolvedVersions = mutableMapOf<String, String>()

        // インストールが必要なプラグインを検出
        val pluginsToInstall = mutableListOf<String>()
        for (pluginName in sortedPlugins) {
            val expectedVersion = mpmConfig.plugins[pluginName] ?: continue
            if (expectedVersion == "unmanaged") continue

            val resolvedVersion = resolveExpectedVersion(pluginName, expectedVersion, resolvedVersions)
            val metadataResult = pluginMetadataManager.loadMetadata(pluginName)

            // インストールが必要かを判定
            val shouldInstall =
                metadataResult.fold(
                    { true }, // メタデータなし → インストール必要
                    { metadata ->
                        // latestは常に最新取得するのでスキップ、それ以外はバージョン比較
                        expectedVersion != "latest" && metadata.mpmInfo.version.current.raw != resolvedVersion
                    }
                )

            // 解決済みバージョンを記録（Sync以外のプラグイン）
            if (!VersionSpecifierParser.isSyncFormat(expectedVersion)) {
                resolvedVersions[pluginName] =
                    metadataResult.fold(
                        { resolvedVersion },
                        { it.mpmInfo.version.current.raw }
                    )
            }

            if (shouldInstall) pluginsToInstall.add(pluginName)
        }

        // インストール結果を記録
        val installed = mutableListOf<PluginInstallInfo>()
        val removed = mutableListOf<PluginRemovalInfo>()
        val failed = mutableMapOf<String, String>()

        // 各プラグインをトポロジカルソート順にインストール
        for (pluginName in sortedPlugins) {
            val versionString = mpmConfig.plugins[pluginName] ?: continue

            if (pluginName !in pluginsToInstall) {
                // インストール不要でもバージョンを記録（後続のSyncプラグインのため）
                if (versionString != "unmanaged" && !VersionSpecifierParser.isSyncFormat(versionString)) {
                    resolvedVersions[pluginName] = resolveExpectedVersion(pluginName, versionString, resolvedVersions)
                }
                continue
            }

            val versionToInstall = resolveExpectedVersion(pluginName, versionString, resolvedVersions)
            installPluginWithVersion(pluginName, versionToInstall).fold(
                { failed[pluginName] = it },
                { result ->
                    installed.add(
                        PluginInstallInfo(
                            name = result.installed.name,
                            currentVersion = result.installed.currentVersion,
                            latestVersion = result.installed.latestVersion
                        )
                    )
                    result.removed?.let {
                        removed.add(
                            PluginRemovalInfo(
                                name = it.name,
                                version = it.version
                            )
                        )
                    }
                    resolvedVersions[pluginName] = result.installed.currentVersion
                }
            )
        }

        return BulkInstallResult(installed = installed, removed = removed, failed = failed).right()
    }

    /**
     * プラグインをロックする
     *
     * LockPluginUseCaseImplから移行したロジック
     */
    override suspend fun lock(name: PluginName): Either<MpmError, Unit> {
        // メタデータを読み込む
        val metadata =
            pluginMetadataManager.loadMetadata(name.value).getOrElse {
                return MpmError.PluginError.MetadataNotFound(name.value).left()
            }

        // 既にロックされている場合はエラー
        if (metadata.mpmInfo.settings.lock == true) {
            return MpmError.PluginError.AlreadyLocked(name.value).left()
        }

        // PluginLockEventを発火して、他のプラグインがキャンセルできるようにする
        val lockEvent =
            PluginLockEvent(
                installedPlugin = InstalledPlugin(name.value),
                currentVersion = metadata.mpmInfo.version.current.raw
            )
        plugin.server.pluginManager.callEvent(lockEvent)

        // イベントがキャンセルされた場合はスキップ
        if (lockEvent.isCancelled) {
            return MpmError.PluginError.OperationCancelled(name.value, "lock").left()
        }

        // ロックフラグを設定
        val updatedMetadata =
            metadata.copy(
                mpmInfo =
                    metadata.mpmInfo.copy(
                        settings = metadata.mpmInfo.settings.copy(lock = true)
                    )
            )

        // メタデータを保存
        pluginMetadataManager.saveMetadata(name.value, updatedMetadata).getOrElse {
            return MpmError.PluginError.MetadataSaveFailed(name.value, it).left()
        }

        return Unit.right()
    }

    /**
     * プラグインのロックを解除する
     *
     * UnlockPluginUseCaseImplから移行したロジック
     */
    override suspend fun unlock(name: PluginName): Either<MpmError, Unit> {
        // メタデータを読み込む
        val metadata =
            pluginMetadataManager.loadMetadata(name.value).getOrElse {
                return MpmError.PluginError.MetadataNotFound(name.value).left()
            }

        // 既にロック解除されている場合はエラー
        if (metadata.mpmInfo.settings.lock != true) {
            return MpmError.PluginError.NotLocked(name.value).left()
        }

        // PluginUnlockEventを発火して、他のプラグインがキャンセルできるようにする
        val unlockEvent =
            PluginUnlockEvent(
                installedPlugin = InstalledPlugin(name.value),
                currentVersion = metadata.mpmInfo.version.current.raw
            )
        plugin.server.pluginManager.callEvent(unlockEvent)

        // イベントがキャンセルされた場合はスキップ
        if (unlockEvent.isCancelled) {
            return MpmError.PluginError.OperationCancelled(name.value, "unlock").left()
        }

        // ロックフラグを解除
        val updatedMetadata =
            metadata.copy(
                mpmInfo =
                    metadata.mpmInfo.copy(
                        settings = metadata.mpmInfo.settings.copy(lock = false)
                    )
            )

        // メタデータを保存
        pluginMetadataManager.saveMetadata(name.value, updatedMetadata).getOrElse {
            return MpmError.PluginError.MetadataSaveFailed(name.value, it).left()
        }

        return Unit.right()
    }

    // === プライベートヘルパーメソッド ===

    /**
     * 更新されたプラグインに同期しているプラグインを連動更新する
     */
    private suspend fun updateSyncPlugins(
        mpmConfig: MpmConfig,
        updatedPlugins: Set<String>,
        updateResults: MutableList<UpdateResult>
    ) {
        // 更新されたプラグインに同期しているプラグインを取得
        val syncPluginsToUpdate = mutableSetOf<String>()
        for (updatedPlugin in updatedPlugins) {
            val syncingPlugins = mpmConfig.getPluginsSyncingTo(updatedPlugin)
            syncPluginsToUpdate.addAll(syncingPlugins)
        }

        // 既に更新済みのプラグインは除外
        syncPluginsToUpdate.removeAll(updatedPlugins)

        // Syncプラグインを更新
        for (syncPluginName in syncPluginsToUpdate) {
            // メタデータを読み込んで現在のバージョンとロック状態を取得
            val metadataEither = pluginMetadataManager.loadMetadata(syncPluginName)
            val currentVersion =
                metadataEither.fold(
                    { "unknown" },
                    { it.mpmInfo.version.current.raw }
                )

            // ロックされている場合はスキップ
            val isLocked =
                metadataEither.fold(
                    { false },
                    { it.mpmInfo.settings.lock == true }
                )
            if (isLocked) {
                updateResults.add(
                    UpdateResult(
                        pluginName = syncPluginName,
                        oldVersion = currentVersion,
                        newVersion = "sync",
                        success = false,
                        errorMessage = "プラグインがロックされています"
                    )
                )
                continue
            }

            // プラグインをインストール
            val installResult = installSinglePlugin(syncPluginName)

            installResult.fold(
                // インストール失敗時
                { errorMessage ->
                    updateResults.add(
                        UpdateResult(
                            pluginName = syncPluginName,
                            oldVersion = currentVersion,
                            newVersion = "sync",
                            success = false,
                            errorMessage = "連動更新に失敗: $errorMessage"
                        )
                    )
                },
                // インストール成功時
                {
                    // 新しいバージョンを取得
                    val newVersion =
                        pluginMetadataManager.loadMetadata(syncPluginName).fold(
                            { "unknown" },
                            { it.mpmInfo.version.current.raw }
                        )
                    updateResults.add(
                        UpdateResult(
                            pluginName = syncPluginName,
                            oldVersion = currentVersion,
                            newVersion = newVersion,
                            success = true
                        )
                    )
                }
            )
        }
    }

    /**
     * 単一のプラグインをインストールする
     *
     * PluginInstallUseCaseImplから移行したロジック
     */
    private suspend fun installSinglePlugin(pluginName: String): Either<String, InstallResult> {
        val metadataDir = pluginDirectory.getMetadataDirectory()
        val metadataFile = File(metadataDir, "$pluginName.yaml")

        if (!metadataFile.exists()) {
            return "メタデータファイルが見つかりません: $pluginName.yaml".left()
        }

        val metadata =
            try {
                val yamlString = metadataFile.readText()
                Yaml.default.decodeFromString(ManagedPluginDto.serializer(), yamlString)
            } catch (e: Exception) {
                return "メタデータの読み込みに失敗しました: ${e.message}".left()
            }

        val mpmInfoDto = metadata.mpmInfo
        val pluginInfoDto = metadata.pluginInfo
        val repositoryInfo = mpmInfoDto.repository

        val urlData =
            createUrlData(repositoryInfo.type.name, repositoryInfo.id)
                ?: return "未対応のリポジトリタイプです: ${repositoryInfo.type.name}".left()

        // 最新バージョンを取得
        val latestVersionData =
            try {
                downloaderRepository.getLatestVersion(urlData)
            } catch (e: Exception) {
                return "最新バージョン情報の取得に失敗しました: ${e.message}".left()
            }

        // メタデータからバージョン情報を作成
        val versionData = VersionData(mpmInfoDto.download.downloadId, mpmInfoDto.version.current.raw)

        // メタデータを更新（最新バージョン情報を反映）
        val updatedMetadataWithLatest =
            pluginMetadataManager
                .updateMetadata(pluginName, versionData, latestVersionData, "install")
                .getOrElse { return it.left() }

        // PluginInstallEventを発火
        val installEvent =
            PluginInstallEvent(
                repositoryPlugin = RepositoryPlugin(pluginName),
                version = versionData.version,
                repositoryType = repositoryInfo.type.name,
                repositoryId = repositoryInfo.id
            )
        plugin.server.pluginManager.callEvent(installEvent)

        // イベントがキャンセルされた場合はスキップ
        if (installEvent.isCancelled) {
            return "インストールがキャンセルされました".left()
        }

        val downloadedFile =
            try {
                downloaderRepository.downloadByVersion(
                    urlData,
                    versionData,
                    mpmInfoDto.fileNamePattern
                )
            } catch (e: Exception) {
                return "プラグインのダウンロードに失敗しました: ${e.message}".left()
            }

        if (downloadedFile == null) {
            return "プラグインファイルのダウンロードに失敗しました。".left()
        }

        val template = mpmInfoDto.fileNameTemplate ?: "<pluginInfo.name>-<mpmInfo.version.current.normalized>.jar"
        val newFileName = generateFileName(template, pluginInfoDto.name, mpmInfoDto.version.current.normalized)

        // 古いファイルを削除（存在する場合）
        val oldFileName = mpmInfoDto.download.fileName
        var removedInfo: PluginRemovalInfo? = null
        if (oldFileName != null && oldFileName != newFileName) {
            val pluginsDir = pluginDirectory.getPluginsDirectory()
            val oldFile = File(pluginsDir, oldFileName)
            if (oldFile.exists()) {
                oldFile.delete()
                removedInfo =
                    PluginRemovalInfo(
                        name = pluginName,
                        version = mpmInfoDto.version.current.normalized
                    )
            }
        }

        val pluginsDir = pluginDirectory.getPluginsDirectory()
        val targetFile = File(pluginsDir, newFileName)
        try {
            downloadedFile.copyTo(targetFile, overwrite = true)
            downloadedFile.delete()
        } catch (e: Exception) {
            return "プラグインファイルの移動に失敗しました: ${e.message}".left()
        }

        // ファイル名をmetadataに記録して保存
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
        pluginMetadataManager.saveMetadata(pluginName, updatedMetadata).getOrElse { return it.left() }

        // インストール結果を返す
        val installInfo =
            PluginInstallInfo(
                name = pluginName,
                currentVersion = updatedMetadata.mpmInfo.version.current.raw,
                latestVersion = updatedMetadata.mpmInfo.version.latest.raw
            )

        return InstallResult(
            installed = installInfo,
            removed = removedInfo
        ).right()
    }

    /**
     * 指定バージョンでプラグインをインストールする
     */
    private suspend fun installPluginWithVersion(
        pluginName: String,
        expectedVersion: String
    ): Either<String, InstallResult> {
        // リポジトリファイルを取得
        val repositoryFile =
            repositoryManager.getRepositoryFile(pluginName)
                ?: return "リポジトリファイルが見つかりません: $pluginName".left()

        val firstRepository =
            repositoryFile.repositories.firstOrNull()
                ?: return "リポジトリ設定が見つかりません: $pluginName".left()

        // UrlDataを作成
        val urlData =
            createUrlData(firstRepository.type, firstRepository.repositoryId)
                ?: return "未対応のリポジトリタイプです: ${firstRepository.type}".left()

        // 最新バージョンを取得
        val latestVersionData =
            try {
                downloaderRepository.getLatestVersion(urlData)
            } catch (e: Exception) {
                return "バージョン情報の取得に失敗しました: ${e.message}".left()
            }

        // 指定バージョンを取得
        val versionData =
            if (expectedVersion == "latest") {
                latestVersionData
            } else {
                try {
                    downloaderRepository.getVersionByName(urlData, expectedVersion)
                } catch (e: Exception) {
                    return "指定されたバージョン '$expectedVersion' の取得に失敗しました: ${e.message}".left()
                }
            }

        // メタデータが存在するか確認し、更新または作成
        val metadata =
            pluginMetadataManager.loadMetadata(pluginName).fold(
                // メタデータが存在しない場合は新規作成
                {
                    pluginMetadataManager
                        .createMetadata(pluginName, firstRepository, versionData, "install")
                        .getOrElse { return it.left() }
                },
                // メタデータが存在する場合は更新
                {
                    pluginMetadataManager
                        .updateMetadata(pluginName, versionData, latestVersionData, "install")
                        .getOrElse { return it.left() }
                }
            )

        // プラグインファイルをダウンロード
        val downloadedFile =
            try {
                downloaderRepository.downloadByVersion(
                    urlData,
                    versionData,
                    firstRepository.fileNamePattern
                )
            } catch (e: Exception) {
                return "プラグインのダウンロードに失敗しました: ${e.message}".left()
            }

        if (downloadedFile == null) {
            return "プラグインファイルのダウンロードに失敗しました。".left()
        }

        // ファイル名を生成
        val template = firstRepository.fileNameTemplate ?: "<pluginInfo.name>-<mpmInfo.version.current.normalized>.jar"
        val newFileName = generateFileName(template, pluginName, metadata.mpmInfo.version.current.normalized)

        // 古いファイルを削除（存在する場合）
        val oldFileName = metadata.mpmInfo.download.fileName
        var removedInfo: PluginRemovalInfo? = null
        if (oldFileName != null && oldFileName != newFileName) {
            val pluginsDir = pluginDirectory.getPluginsDirectory()
            val oldFile = File(pluginsDir, oldFileName)
            if (oldFile.exists()) {
                oldFile.delete()
                pluginMetadataManager.loadMetadata(pluginName).onRight { existingMetadata ->
                    removedInfo =
                        PluginRemovalInfo(
                            name = pluginName,
                            version = existingMetadata.mpmInfo.version.current.normalized
                        )
                }
            }
        }

        // プラグインディレクトリにコピー
        val pluginsDir = pluginDirectory.getPluginsDirectory()
        val targetFile = File(pluginsDir, newFileName)
        try {
            downloadedFile.copyTo(targetFile, overwrite = true)
            downloadedFile.delete()
        } catch (e: Exception) {
            return "プラグインファイルの移動に失敗しました: ${e.message}".left()
        }

        // ファイル名をmetadataに記録して保存
        val updatedMetadata =
            metadata.copy(
                mpmInfo =
                    metadata.mpmInfo.copy(
                        download =
                            metadata.mpmInfo.download.copy(
                                fileName = newFileName
                            )
                    )
            )
        pluginMetadataManager.saveMetadata(pluginName, updatedMetadata).getOrElse { return it.left() }

        // インストール結果を返す
        val installInfo =
            PluginInstallInfo(
                name = pluginName,
                currentVersion = metadata.mpmInfo.version.current.raw,
                latestVersion = metadata.mpmInfo.version.latest.raw
            )

        return InstallResult(
            installed = installInfo,
            removed = removedInfo
        ).right()
    }

    /**
     * mpm.jsonを読み込む
     */
    private fun loadMpmConfig(): MpmConfig? {
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "mpm.json")

        if (!configFile.exists()) {
            return null
        }

        return try {
            val jsonString = configFile.readText()
            Utils.json.decodeFromString<MpmConfig>(jsonString)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * バージョン指定文字列を実際のバージョンに解決する
     */
    private fun resolveExpectedVersion(
        pluginName: String,
        expected: String,
        resolved: Map<String, String>
    ): String {
        val syncTarget = VersionSpecifierParser.extractSyncTarget(expected)
        return when {
            syncTarget != null -> resolved[syncTarget] ?: expected
            expected == "latest" ->
                pluginMetadataManager.loadMetadata(pluginName).fold(
                    { expected },
                    { it.mpmInfo.version.current.raw }
                )
            else -> expected
        }
    }

    /**
     * リポジトリタイプとIDからUrlDataを作成するヘルパーメソッド
     */
    private fun createUrlData(
        type: String,
        repositoryId: String
    ): UrlData? {
        return when (type.lowercase()) {
            "github" -> {
                val parts = repositoryId.split("/")
                if (parts.size != 2) return null
                UrlData.GithubUrlData(owner = parts[0], repository = parts[1])
            }
            "modrinth" -> UrlData.ModrinthUrlData(id = repositoryId)
            "spigotmc" -> UrlData.SpigotMcUrlData(resourceId = repositoryId)
            else -> null
        }
    }

    /**
     * ファイル名を生成する
     */
    private fun generateFileName(
        template: String,
        pluginName: String,
        versionString: String
    ): String {
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