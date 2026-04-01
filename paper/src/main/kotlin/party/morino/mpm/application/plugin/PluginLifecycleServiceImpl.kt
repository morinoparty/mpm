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
import party.morino.mpm.api.application.model.AddWithDependenciesResult
import party.morino.mpm.api.application.model.AdoptResult
import party.morino.mpm.api.application.model.InstallResult
import party.morino.mpm.api.application.model.PluginAddResult
import party.morino.mpm.api.application.model.PluginFilter
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.domain.compatibility.ApiVersionChecker
import party.morino.mpm.api.domain.compatibility.CompatibilityResult
import party.morino.mpm.api.application.model.PluginInstallInfo
import party.morino.mpm.api.application.model.PluginRemovalInfo
import party.morino.mpm.api.application.plugin.PluginLifecycleService
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.downloader.DownloaderRepository
import party.morino.mpm.api.domain.downloader.model.UrlData
import party.morino.mpm.api.domain.downloader.model.VersionData
import party.morino.mpm.api.domain.plugin.model.ManagedPlugin
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.domain.plugin.model.PluginSpec
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import party.morino.mpm.api.domain.project.dto.getPluginsSyncingTo
import party.morino.mpm.api.domain.project.model.MpmProject
import party.morino.mpm.api.domain.project.repository.ProjectRepository
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
import party.morino.mpm.utils.BukkitDispatcher
import party.morino.mpm.utils.DataClassReplacer.replaceTemplate
import party.morino.mpm.utils.PluginDataUtils
import java.io.File
import party.morino.mpm.api.domain.plugin.model.VersionSpecifierParser
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
    private val projectRepository: ProjectRepository by inject()
    private val repositoryManager: RepositoryManager by inject()
    private val downloaderRepository: DownloaderRepository by inject()
    private val metadataManager: PluginMetadataManager by inject()
    private val plugin: JavaPlugin by inject()
    private val infoService: PluginInfoService by inject()
    private val apiVersionChecker: ApiVersionChecker by inject()

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

        // ProjectRepositoryを通じてプロジェクトを取得（パースエラーも区別する）
        val project = projectRepository.findOrError().getOrElse { error ->
            return when (error) {
                is MpmError.ProjectError.ConfigNotFound -> MpmError.ProjectError.NotInitialized.left()
                else -> error.left()
            }
        }

        // リポジトリソースからプラグインが存在するか確認
        val repositoryFile =
            repositoryManager.getRepositoryFile(pluginName)
                ?: return MpmError.DownloadError.RepositoryNotFound("unknown", pluginName).left()

        // Latestが指定されている場合、リポファイルのdefaultVersionがあればそちらを使用
        val resolvedVersion = if (version is VersionSpecifier.Latest && repositoryFile.defaultVersion != null) {
            VersionSpecifierParser.parse(repositoryFile.defaultVersion!!)
        } else {
            version
        }
        val legacyVersion = toLegacyVersionSpecifier(resolvedVersion)

        // リポジトリ設定から最初のリポジトリを取得
        val firstRepository =
            repositoryFile.repositories.firstOrNull()
                ?: return MpmError.DownloadError.RepositoryNotFound("config", pluginName).left()

        // RepositoryConfigからUrlDataを作成
        val urlData =
            createUrlData(firstRepository)
                ?: return MpmError.DownloadError.RepositoryNotFound(firstRepository.type, pluginName).left()

        // 既に追加されているか確認（unmanagedの場合は除外）
        val existingSpec = project.getPluginSpec(name)
        if (existingSpec != null && existingSpec !is PluginSpec.Unmanaged) {
            return MpmError.PluginError.AlreadyExists(pluginName).left()
        }

        // VersionSpecifierに応じてバージョンデータを決定
        val versionData: VersionData =
            resolveVersionData(
                legacyVersion,
                urlData,
                project,
                pluginName
            ).getOrElse { return it.left() }

        // メタデータを作成
        val metadata =
            metadataManager
                .createMetadata(pluginName, firstRepository, versionData, "add")
                .getOrElse { return MpmError.PluginError.AddFailed(pluginName, it).left() }

        // PluginAddEventを発火して、他のプラグインがキャンセルできるようにする
        // PaperMCではイベントはメインスレッドで発火する必要があるため、BukkitDispatcherを使用
        val addEvent =
            BukkitDispatcher.callEventSync(
                plugin,
                PluginAddEvent(
                    repositoryPlugin = RepositoryPlugin(pluginName),
                    versionSpecifier = legacyVersion,
                    repositoryType = firstRepository.type,
                    repositoryId = firstRepository.repositoryId
                )
            )

        // イベントがキャンセルされた場合はスキップ
        if (addEvent.isCancelled) {
            return MpmError.PluginError.AddFailed(pluginName, "Cancelled by event").left()
        }

        // MpmProjectのプラグインマップを更新
        // Fixed指定時はリポジトリから解決された正規バージョン名を使用する
        val resolvedVersionSpec = when (version) {
            is VersionSpecifier.Fixed -> VersionSpecifier.Fixed(versionData.version)
            else -> version
        }
        val newSpec = PluginSpec.Managed(name, resolvedVersionSpec)
        val updatedProject = if (existingSpec != null) {
            // unmanagedからの変換
            project.updatePlugin(name, newSpec).getOrElse {
                return MpmError.PluginError.AddFailed(pluginName, it.message).left()
            }
        } else {
            // 新規追加
            project.addPlugin(newSpec).getOrElse {
                return MpmError.PluginError.AddFailed(pluginName, it.message).left()
            }
        }
        val sortedProject = updatedProject.withSortedPlugins()

        // ロールバック用に既存メタデータを退避（unmanagedからの変換時に既存データがある場合）
        val previousMetadata = metadataManager.loadMetadata(pluginName).getOrNull()

        // メタデータを先に保存（mpm.jsonより先に保存することで、メタデータ保存失敗時の不整合を防ぐ）
        metadataManager
            .saveMetadata(pluginName, metadata)
            .getOrElse { return MpmError.PluginError.AddFailed(pluginName, it).left() }

        // メタデータ保存成功後にProjectRepositoryを通じて保存
        try {
            projectRepository.save(sortedProject)
        } catch (e: Exception) {
            // 保存失敗時はメタデータをロールバック（以前の状態に復元）
            val rollbackError = if (previousMetadata != null) {
                metadataManager.saveMetadata(pluginName, previousMetadata).fold(
                    { rollbackMsg -> " (rollback also failed: $rollbackMsg)" },
                    { "" }
                )
            } else {
                metadataManager.deleteMetadata(pluginName).fold(
                    { rollbackMsg -> " (rollback also failed: $rollbackMsg)" },
                    { "" }
                )
            }
            return MpmError.PluginError.AddFailed(pluginName, "Failed to save mpm.json: ${e.message}$rollbackError").left()
        }

        // ManagedPluginを返す（メタデータから構築）
        return ManagedPlugin.fromDto(metadata).right()
    }

    /**
     * プラグインを管理対象から削除する
     *
     * RemovePluginUseCaseImpl から移行したロジック
     */
    override suspend fun remove(
        name: PluginName,
        force: Boolean
    ): Either<MpmError, Unit> {
        val pluginName = name.value

        // ProjectRepositoryを通じてプロジェクトを取得（パースエラーも区別する）
        val project = projectRepository.findOrError().getOrElse { error ->
            return when (error) {
                is MpmError.ProjectError.ConfigNotFound -> MpmError.ProjectError.NotInitialized.left()
                else -> error.left()
            }
        }

        // プラグインが管理対象に含まれているか確認
        if (project.getPluginSpec(name) == null) {
            return MpmError.PluginError.NotFound(pluginName).left()
        }

        // 逆依存関係チェック（このプラグインにsyncしているプラグインがあるか）
        val dependents = project.toDto().getPluginsSyncingTo(pluginName)
        if (dependents.isNotEmpty()) {
            if (!force) {
                return MpmError.PluginError.HasDependents(pluginName, dependents).left()
            }
            // forceの場合は警告を出して続行（sync設定がdanglingになる）
            plugin.logger.warning(
                "Force removing '$pluginName' which has dependents: ${dependents.joinToString(", ")}. " +
                    "Their sync configuration will be broken."
            )
        }

        // PluginRemoveEventを発火して、他のプラグインがキャンセルできるようにする
        // PaperMCではイベントはメインスレッドで発火する必要があるため、BukkitDispatcherを使用
        val removeEvent =
            BukkitDispatcher.callEventSync(
                plugin,
                PluginRemoveEvent(
                    installedPlugin = InstalledPlugin(pluginName)
                )
            )

        // イベントがキャンセルされた場合はエラー
        if (removeEvent.isCancelled) {
            return MpmError.PluginError.RemoveFailed(pluginName, "Cancelled by event").left()
        }

        // MpmProjectからプラグインを削除
        val updatedProject = project.removePlugin(name).getOrElse {
            return MpmError.PluginError.RemoveFailed(pluginName, it.message).left()
        }
        val sortedProject = updatedProject.withSortedPlugins()

        // ProjectRepositoryを通じて保存
        return try {
            projectRepository.save(sortedProject)
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
    override suspend fun install(
        name: PluginName,
        force: Boolean
    ): Either<MpmError, InstallResult> {
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

        // mpm.jsonからVersionSpecifierを取得してTag指定か判定
        val project = projectRepository.find()
        val pluginSpec = project?.getPluginSpec(name)
        val versionSpecifier = (pluginSpec as? PluginSpec.Managed)?.versionRequirement

        // 最新バージョン情報を取得（Tag指定の場合はそのタグでフィルタ）
        val latestVersionData =
            try {
                if (versionSpecifier is VersionSpecifier.Tag) {
                    // Tag指定: 該当タグの最新バージョンを取得（見つからなければエラー）
                    downloaderRepository.getLatestVersionByTag(urlData, versionSpecifier.tag)
                        ?: return MpmError.PluginError
                            .VersionResolutionFailed(
                                pluginName,
                                "tag '${versionSpecifier.tag}' に該当するバージョンが見つかりません"
                            ).left()
                } else {
                    downloaderRepository.getLatestVersion(urlData)
                }
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
        // PaperMCではイベントはメインスレッドで発火する必要があるため、BukkitDispatcherを使用
        val installEvent =
            BukkitDispatcher.callEventSync(
                plugin,
                PluginInstallEvent(
                    repositoryPlugin = RepositoryPlugin(pluginName),
                    version = versionData.version,
                    repositoryType = repositoryInfo.type.name,
                    repositoryId = repositoryInfo.id
                )
            )

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

        // APIバージョンの互換性チェック
        val compatibilityResult = apiVersionChecker.checkCompatibility(downloadedFile)
        when (compatibilityResult) {
            is CompatibilityResult.Incompatible -> {
                if (!force) {
                    // 非互換かつforceでない場合、一時ファイルを削除してエラーを返す
                    downloadedFile.delete()
                    return MpmError.PluginError.ApiVersionIncompatible(
                        pluginName,
                        compatibilityResult.pluginApiVersion,
                        compatibilityResult.serverApiVersion
                    ).left()
                }
                // forceの場合は警告ログを出して続行
                plugin.logger.warning(
                    "api-version incompatible ($pluginName): " +
                        "plugin=${compatibilityResult.pluginApiVersion}, " +
                        "server=${compatibilityResult.serverApiVersion}. Forced install."
                )
            }
            is CompatibilityResult.Unknown -> {
                // 判定不能の場合はログに警告を出して続行
                plugin.logger.warning(
                    "Cannot verify api-version compatibility ($pluginName): ${compatibilityResult.reason}"
                )
            }
            is CompatibilityResult.Compatible -> {
                // 互換性あり、続行
            }
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

        // ProjectRepositoryを通じてプロジェクトを取得（パースエラーも区別する）
        val project = projectRepository.findOrError().getOrElse { error ->
            return when (error) {
                is MpmError.ProjectError.ConfigNotFound -> MpmError.ProjectError.NotInitialized.left()
                else -> error.left()
            }
        }

        // プラグインが管理対象に含まれているか確認
        if (project.getPluginSpec(name) == null) {
            return MpmError.PluginError.NotFound(pluginName).left()
        }

        // pluginsディレクトリから対象のJARファイルを特定
        val targetJarFile = findJarForPlugin(pluginName)?.first

        // PluginUninstallEventを発火して、他のプラグインがキャンセルできるようにする
        // PaperMCではイベントはメインスレッドで発火する必要があるため、BukkitDispatcherを使用
        val uninstallEvent =
            BukkitDispatcher.callEventSync(
                plugin,
                PluginUninstallEvent(
                    installedPlugin = InstalledPlugin(pluginName),
                    jarFile = targetJarFile
                )
            )

        // イベントがキャンセルされた場合はエラー
        if (uninstallEvent.isCancelled) {
            return MpmError.PluginError.OperationCancelled(pluginName, "uninstall").left()
        }

        // JARファイルを削除
        targetJarFile?.delete()

        // MpmProjectからプラグインを削除
        val updatedProject = project.removePlugin(name).getOrElse {
            return MpmError.PluginError.UninstallFailed(pluginName, it.message).left()
        }
        val sortedProject = updatedProject.withSortedPlugins()

        // ProjectRepositoryを通じて保存
        return try {
            projectRepository.save(sortedProject)
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
        // ProjectRepositoryを通じてプロジェクトを取得（パースエラーも区別する）
        val project = projectRepository.findOrError().getOrElse { error ->
            return when (error) {
                is MpmError.ProjectError.ConfigNotFound -> MpmError.ProjectError.NotInitialized.left()
                else -> error.left()
            }
        }

        // 管理対象のプラグイン名セット
        val managedPlugins = project.plugins.keys.map { it.value }.toSet()

        // pluginsディレクトリからJARファイルを取得
        val pluginsDir = pluginDirectory.getPluginsDirectory()
        val pluginFiles =
            pluginsDir.listFiles { file ->
                file.isFile && file.extension == "jar"
            } ?: emptyArray()

        // localディレクトリを取得（localディレクトリ内のプラグインは削除対象外）
        val rootDir = pluginDirectory.getRootDirectory()
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
     * pluginsディレクトリから指定されたプラグイン名のJARファイルとPluginDataを検索する
     *
     * @param pluginName 検索するプラグイン名
     * @return JARファイルとPluginDataのペア、見つからない場合はnull
     */
    private fun findJarForPlugin(pluginName: String): Pair<File, PluginData>? {
        val pluginsDir = pluginDirectory.getPluginsDirectory()
        val pluginFiles = pluginsDir.listFiles { file ->
            file.isFile && file.extension == "jar"
        } ?: return null

        for (jarFile in pluginFiles) {
            try {
                val pluginData = PluginDataUtils.getPluginData(jarFile) ?: continue
                val jarPluginName = when (pluginData) {
                    is PluginData.BukkitPluginData -> pluginData.name
                    is PluginData.PaperPluginData -> pluginData.name
                }
                if (jarPluginName == pluginName) {
                    return jarFile to pluginData
                }
            } catch (e: Exception) {
                // パース不能なJARはスキップ
                continue
            }
        }
        return null
    }

    /**
     * PluginDataからバージョン文字列を取得する
     */
    private fun getVersionFromPluginData(pluginData: PluginData): String =
        when (pluginData) {
            is PluginData.BukkitPluginData -> pluginData.version
            is PluginData.PaperPluginData -> pluginData.version
        }

    /**
     * ファイルのSHA-1ハッシュを計算する
     *
     * @param file ハッシュを計算するファイル
     * @return SHA-1ハッシュの16進数文字列
     */
    private fun computeSha1(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-1")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * バージョンpinの結果を表すsealed class
     */
    private sealed class VersionPinResult {
        /** バージョンの固定に成功 */
        data class Pinned(
            val version: VersionSpecifier.Fixed,
            val hashWarning: String? = null
        ) : VersionPinResult()

        /** バージョンが見つからずLatestにフォールバック */
        data class FallbackToLatest(val reason: String) : VersionPinResult()

        /** 通信/API失敗などの回復不能エラー（失敗扱い） */
        data class Error(val reason: String) : VersionPinResult()
    }

    /**
     * 既存JARのplugin.ymlからバージョンを検出し、リポジトリで検索してpin用のVersionSpecifierを返す
     *
     * @param pluginName リポジトリ上のプラグイン名
     * @param unmanagedName mpm.json上のunmanaged名（JAR内のプラグイン名）
     * @param urlData リポジトリのURLデータ
     * @return VersionPinResult（Pinned or FallbackToLatest）
     */
    private suspend fun resolveCurrentVersionFromJar(
        pluginName: String,
        unmanagedName: String,
        urlData: UrlData,
        progressCallback: ((String) -> Unit)? = null
    ): VersionPinResult {
        // 既存JARを検索
        val (jarFile, pluginData) = findJarForPlugin(unmanagedName)
            ?: return VersionPinResult.FallbackToLatest("JARファイルが見つかりません")

        val currentVersion = getVersionFromPluginData(pluginData).trim()
        if (currentVersion.isBlank()) {
            return VersionPinResult.FallbackToLatest("plugin.ymlにバージョンが記載されていません")
        }

        progressCallback?.invoke("<gray>[$pluginName] JARバージョン: $currentVersion — リポジトリで検索中...")

        // リポジトリの全バージョンを1回取得（通信失敗は失敗扱い）
        val allVersions = try {
            downloaderRepository.getAllVersions(urlData)
        } catch (e: Exception) {
            // 通信/API失敗はlatestフォールバックではなく失敗として報告
            return VersionPinResult.Error(
                "リポジトリへの接続に失敗しました: ${e.message}"
            )
        }

        // 表記揺れを考慮したバージョン候補を生成
        val versionCandidates = buildVersionCandidates(currentVersion)

        // ローカルでバージョン名を照合
        val resolvedVersionData = versionCandidates.firstNotNullOfOrNull { candidate ->
            allVersions.firstOrNull { it.version == candidate }
        }

        if (resolvedVersionData == null) {
            progressCallback?.invoke("<yellow>[$pluginName] バージョン '$currentVersion' がリポジトリに見つかりません")
            return VersionPinResult.FallbackToLatest(
                "バージョン '$currentVersion' がリポジトリに見つかりません"
            )
        }

        progressCallback?.invoke("<gray>[$pluginName] バージョン '${resolvedVersionData.version}' が一致 — ハッシュ検証中...")

        // ハッシュ検証（APIでハッシュが取得できるリポジトリのみ）
        val hashWarning = verifyHashIfAvailable(urlData, resolvedVersionData.version, jarFile, progressCallback, pluginName)

        return VersionPinResult.Pinned(
            version = VersionSpecifier.Fixed(resolvedVersionData.version),
            hashWarning = hashWarning
        )
    }

    /**
     * バージョン文字列から表記揺れ候補を生成する
     *
     * "v" prefixの有無を切り替えた候補を返す。
     * "v" の除去は "v1.0.0" のように数字が続く場合のみ行う（"Version" 等の誤切断を防止）。
     */
    private fun buildVersionCandidates(version: String): List<String> = buildList {
        add(version)
        val vPrefixPattern = Regex("^[vV](?=\\d)")
        if (vPrefixPattern.containsMatchIn(version)) {
            // "v1.0.0" → "1.0.0" も候補に追加
            add(version.substring(1))
        } else if (version.firstOrNull()?.isDigit() == true) {
            // "1.0.0" → "v1.0.0" も候補に追加
            add("v$version")
        }
    }

    /**
     * APIでハッシュが取得可能な場合、既存JARのハッシュと比較検証する
     *
     * 複数artifactがある場合は、どれか1つでもsha1が一致すればOKとする。
     *
     * @param urlData リポジトリのURLデータ
     * @param versionName リポジトリ上のバージョン名
     * @param localJar ローカルのJARファイル
     * @return 不一致や取得不能の場合は警告メッセージ、問題なしの場合はnull
     */
    private suspend fun verifyHashIfAvailable(
        urlData: UrlData,
        versionName: String,
        localJar: File,
        progressCallback: ((String) -> Unit)? = null,
        pluginName: String? = null
    ): String? {
        val label = pluginName?.let { "[$it] " } ?: ""
        val repoHashes = try {
            downloaderRepository.getVersionHashesByName(urlData, versionName)
        } catch (_: Exception) {
            progressCallback?.invoke("<gray>${label}ハッシュ取得不可 — スキップ")
            return null // ハッシュ取得に失敗した場合は警告なしでスキップ
        }

        // ハッシュ非対応のリポジトリ
        if (repoHashes == null) {
            progressCallback?.invoke("<gray>${label}ハッシュ非対応リポジトリ — スキップ")
            return null
        }

        val repoSha1Values = repoHashes["sha1"]?.split(",") ?: return null

        progressCallback?.invoke("<gray>${label}SHA-1ハッシュを計算中...")
        val localSha1 = computeSha1(localJar)
        progressCallback?.invoke("<gray>${label}ローカル SHA-1: $localSha1")
        repoSha1Values.forEachIndexed { index, repoSha1 ->
            val suffix = if (repoSha1Values.size > 1) " [artifact ${index + 1}]" else ""
            progressCallback?.invoke("<gray>${label}リモート SHA-1: ${repoSha1.trim()}$suffix")
        }

        // 複数artifactのどれか1つでもsha1が一致すればOK（大文字小文字無視）
        val matched = repoSha1Values.any { it.trim().equals(localSha1, ignoreCase = true) }
        return if (!matched) {
            progressCallback?.invoke("<yellow>${label}ハッシュ不一致: ローカルJARとリポジトリのバージョンが異なる可能性があります")
            "ハッシュ不一致: ローカルJARがリポジトリのバージョンと異なる可能性があります (local=$localSha1, remote=${repoSha1Values.joinToString(",") { it.trim() }})"
        } else {
            progressCallback?.invoke("<green>${label}ハッシュ一致 ✓")
            null // ハッシュ一致、問題なし
        }
    }

    /**
     * VersionSpecifierに応じてバージョンデータを解決する
     */
    private suspend fun resolveVersionData(
        version: LegacyVersionSpecifier,
        urlData: UrlData,
        project: MpmProject,
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
                    // 指定されたバージョンのdownloadIdを正しく取得する
                    downloaderRepository.getVersionByName(urlData, version.version).right()
                } catch (e: Exception) {
                    MpmError.PluginError.VersionResolutionFailed(pluginName, e.message ?: "Unknown error").left()
                }
            }
            is LegacyVersionSpecifier.Tag -> {
                try {
                    val result = downloaderRepository.getLatestVersionByTag(urlData, version.tag)
                    result?.right()
                        ?: MpmError.PluginError.VersionResolutionFailed(
                            pluginName,
                            "tag '${version.tag}' に該当するバージョンが見つかりません"
                        ).left()
                } catch (e: Exception) {
                    MpmError.PluginError.VersionResolutionFailed(pluginName, e.message ?: "Unknown error").left()
                }
            }
            is LegacyVersionSpecifier.Pattern -> {
                MpmError.PluginError.VersionResolutionFailed(pluginName, "pattern: specifier is not yet implemented. Use 'latest' or a specific version instead.").left()
            }
            is LegacyVersionSpecifier.Sync -> {
                resolveSyncVersion(version, urlData, project, pluginName)
            }
        }

    /**
     * Sync バージョンを解決する
     */
    private suspend fun resolveSyncVersion(
        version: LegacyVersionSpecifier.Sync,
        urlData: UrlData,
        project: MpmProject,
        pluginName: String
    ): Either<MpmError, VersionData> {
        // ターゲットプラグインがプロジェクトに存在するか確認
        val targetSpec =
            project.getPluginSpec(PluginName(version.targetPlugin))
                ?: return MpmError.PluginError
                    .VersionResolutionFailed(
                        pluginName,
                        "Sync target '${version.targetPlugin}' not found"
                    ).left()

        // ターゲットがunmanagedの場合はエラー
        if (targetSpec is PluginSpec.Unmanaged) {
            return MpmError.PluginError
                .VersionResolutionFailed(
                    pluginName,
                    "Sync target '${version.targetPlugin}' is unmanaged"
                ).left()
        }

        // ターゲットのバージョン指定を取得
        val targetManaged = targetSpec as PluginSpec.Managed

        // ターゲットもSync指定の場合はエラー
        if (targetManaged.versionRequirement is VersionSpecifier.Sync) {
            return MpmError.PluginError
                .VersionResolutionFailed(
                    pluginName,
                    "Sync target '${version.targetPlugin}' is also sync"
                ).left()
        }

        // ターゲットのバージョンを解決
        val resolvedVersion = when (targetManaged.versionRequirement) {
            is VersionSpecifier.Latest, is VersionSpecifier.Tag -> {
                // Latest/Tag: メタデータがあれば現在のバージョンを使用、なければリポジトリから解決
                metadataManager.loadMetadata(version.targetPlugin).fold(
                    {
                        // メタデータがない場合はターゲットのリポジトリからバージョンを取得
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
                            val targetReq = targetManaged.versionRequirement
                            if (targetReq is VersionSpecifier.Tag) {
                                // Tag指定: 該当チャンネルの最新バージョンを取得
                                downloaderRepository.getLatestVersionByTag(targetUrlData, targetReq.tag)?.version
                                    ?: return MpmError.PluginError
                                        .VersionResolutionFailed(
                                            pluginName,
                                            "tag '${targetReq.tag}' に該当するバージョンが見つかりません"
                                        ).left()
                            } else {
                                downloaderRepository.getLatestVersion(targetUrlData).version
                            }
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
            }
            is VersionSpecifier.Fixed -> {
                // Fixed: 指定されたバージョン文字列をそのまま使用
                (targetManaged.versionRequirement as VersionSpecifier.Fixed).version
            }
            else -> {
                // Pattern等: DTO経由で取得
                val dto = project.toDto()
                dto.plugins[version.targetPlugin] ?: return MpmError.PluginError
                    .VersionResolutionFailed(pluginName, "Target version not found").left()
            }
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

    /**
     * プラグインを依存関係と共に追加・インストールする
     *
     * 依存関係を再帰的に解決し、必要なすべてのプラグインを追加・インストールする
     * 依存関係は先にインストールされ、その後メインプラグインがインストールされる
     *
     * @param name プラグイン名
     * @param version バージョン指定
     * @param includeSoftDependencies softDependenciesも含めるかどうか
     * @return 追加結果
     */
    override suspend fun addWithDependencies(
        name: PluginName,
        version: VersionSpecifier,
        includeSoftDependencies: Boolean,
        force: Boolean
    ): Either<MpmError, AddWithDependenciesResult> {
        val addedPlugins = mutableListOf<PluginAddResult>()
        val skippedPlugins = mutableListOf<String>()
        val failedPlugins = mutableMapOf<String, String>()
        val notFoundPlugins = mutableListOf<String>()
        // 処理済みプラグインを追跡（循環依存防止）
        val processedPlugins = mutableSetOf<String>()

        // 再帰的に依存関係を処理
        addPluginRecursively(
            pluginName = name.value,
            version = version,
            isDependency = false,
            includeSoftDependencies = includeSoftDependencies,
            force = force,
            processedPlugins = processedPlugins,
            addedPlugins = addedPlugins,
            skippedPlugins = skippedPlugins,
            failedPlugins = failedPlugins,
            notFoundPlugins = notFoundPlugins
        )

        return AddWithDependenciesResult(
            addedPlugins = addedPlugins,
            skippedPlugins = skippedPlugins,
            failedPlugins = failedPlugins,
            notFoundPlugins = notFoundPlugins
        ).right()
    }

    /**
     * プラグインを再帰的に追加する内部メソッド
     *
     * 依存関係を先に処理し、その後メインプラグインを処理する
     */
    private suspend fun addPluginRecursively(
        pluginName: String,
        version: VersionSpecifier,
        isDependency: Boolean,
        includeSoftDependencies: Boolean,
        force: Boolean,
        processedPlugins: MutableSet<String>,
        addedPlugins: MutableList<PluginAddResult>,
        skippedPlugins: MutableList<String>,
        failedPlugins: MutableMap<String, String>,
        notFoundPlugins: MutableList<String>
    ) {
        // 既に処理済みの場合はスキップ（循環依存防止）
        if (pluginName in processedPlugins) {
            return
        }
        processedPlugins.add(pluginName)

        // リポジトリからプラグイン情報を取得
        val repositoryFile = repositoryManager.getRepositoryFile(pluginName)
        if (repositoryFile == null) {
            // 依存関係として必要だがリポジトリに存在しない場合
            if (isDependency) {
                notFoundPlugins.add(pluginName)
            } else {
                failedPlugins[pluginName] = "リポジトリに見つかりませんでした"
            }
            return
        }

        // 依存関係を先に処理（再帰）
        val dependencies = repositoryFile.dependencies
        val softDependencies = if (includeSoftDependencies) repositoryFile.softDependencies else emptyList()
        val allDependencies = dependencies + softDependencies

        for (depName in allDependencies) {
            // 依存プラグインのリポファイルにdefaultVersionがあればそれを使用
            val depRepoFile = repositoryManager.getRepositoryFile(depName)
            val depVersion = depRepoFile?.defaultVersion
                ?.let { VersionSpecifierParser.parse(it) }
                ?: VersionSpecifier.Latest

            addPluginRecursively(
                pluginName = depName,
                version = depVersion,
                isDependency = true,
                includeSoftDependencies = includeSoftDependencies,
                force = force,
                processedPlugins = processedPlugins,
                addedPlugins = addedPlugins,
                skippedPlugins = skippedPlugins,
                failedPlugins = failedPlugins,
                notFoundPlugins = notFoundPlugins
            )
        }

        // ProjectRepositoryを通じて、既に追加済みかどうかチェック
        val currentProject = projectRepository.find()
        if (currentProject != null) {
            val existingSpec = currentProject.getPluginSpec(PluginName(pluginName))
            // 既に追加済み（unmanagedでない）の場合はスキップ
            if (existingSpec != null && existingSpec !is PluginSpec.Unmanaged) {
                skippedPlugins.add(pluginName)
                return
            }
        }

        // Latestが指定されている場合、リポファイルのdefaultVersionがあればそちらを使用
        val resolvedVersion = if (version is VersionSpecifier.Latest && repositoryFile.defaultVersion != null) {
            VersionSpecifierParser.parse(repositoryFile.defaultVersion!!)
        } else {
            version
        }

        // プラグインを追加
        val addResult = add(PluginName(pluginName), resolvedVersion)
        addResult.fold(
            { error ->
                failedPlugins[pluginName] = error.message
            },
            {
                // 追加成功後、インストール（forceフラグを伝播）
                val installResult = install(PluginName(pluginName), force)
                installResult.fold(
                    { error ->
                        failedPlugins[pluginName] = "追加成功、インストール失敗: ${error.message}"
                    },
                    { result ->
                        addedPlugins.add(
                            PluginAddResult(
                                pluginName = pluginName,
                                installResult = result,
                                isDependency = isDependency
                            )
                        )
                    }
                )
            }
        )
    }

    /**
     * すべてのunmanagedプラグインをリポジトリから検索してadoptする
     *
     * unmanagedプラグイン（mpm.jsonで"unmanaged"として登録されているプラグイン）を
     * リポジトリから検索し、見つかった場合はmanaged状態に変更してダウンロードする
     *
     * @param includeSoftDependencies softDependenciesも含めるかどうか
     * @param pinToCurrentVersion trueの場合、既存JARのバージョンに固定する
     * @return adopt結果（adoptされたプラグイン、スキップされたプラグイン、失敗したプラグイン）
     */
    override suspend fun adoptAll(
        includeSoftDependencies: Boolean,
        pinToCurrentVersion: Boolean,
        progressCallback: ((String) -> Unit)?
    ): Either<MpmError, AdoptResult> {
        // unmanagedプラグイン一覧を取得
        val unmanagedPlugins = infoService.list(PluginFilter.UNMANAGED)
        val unmanagedNames = unmanagedPlugins.map { it.name.value }

        // リポジトリの利用可能なプラグイン一覧を取得
        val availablePlugins = repositoryManager.getAvailablePlugins()

        // unmanagedプラグインとリポジトリをマッチング（大文字小文字を無視）
        // リポジトリのプラグイン名をキー（小文字）、元の名前を値としたマップを作成
        val availablePluginsMap = availablePlugins.associateBy { it.lowercase() }

        // マッチしたプラグインと見つからなかったプラグインを分類
        val matchedPlugins = mutableListOf<Pair<String, String>>() // unmanagedName to repoName
        val skippedPlugins = mutableListOf<String>()

        for (unmanagedName in unmanagedNames) {
            val repoName = availablePluginsMap[unmanagedName.lowercase()]
            if (repoName != null) {
                // リポジトリに見つかった場合、元のリポジトリの名前を使用
                matchedPlugins.add(unmanagedName to repoName)
            } else {
                // リポジトリに見つからなかった場合
                skippedPlugins.add(unmanagedName)
            }
        }

        // マッチしたプラグインをadopt（addWithDependenciesを使用）
        val adoptedPlugins = mutableListOf<PluginAddResult>()
        val failedPlugins = mutableMapOf<String, String>()
        val notFoundDependencies = mutableListOf<String>()
        val pinnedPlugins = mutableListOf<String>()
        val hashMismatchWarnings = mutableMapOf<String, String>()
        val versionMismatchPlugins = mutableMapOf<String, String>()

        for ((unmanagedName, repoName) in matchedPlugins) {
            progressCallback?.invoke("<gray>[$repoName] バージョン解決中...")

            // バージョン指定を決定（--pin時はJARのバージョンに固定を試みる）
            val pinResult = if (pinToCurrentVersion) {
                resolveVersionForPin(unmanagedName, repoName, progressCallback)
            } else {
                null
            }

            // pin解決がErrorの場合は失敗扱い
            if (pinResult is VersionPinResult.Error) {
                failedPlugins[repoName] = pinResult.reason
                continue
            }

            // --pin時にバージョンが一致しなかった場合はunmanagedのまま残す
            if (pinResult is VersionPinResult.FallbackToLatest) {
                progressCallback?.invoke("<yellow>[$repoName] ${pinResult.reason} — unmanagedのまま残します")
                versionMismatchPlugins[repoName] = pinResult.reason
                continue
            }

            val versionSpecifier = when (pinResult) {
                is VersionPinResult.Pinned -> {
                    progressCallback?.invoke("<green>[$repoName] バージョン ${pinResult.version.version} に固定")
                    pinResult.version
                }
                else -> VersionSpecifier.Latest
            }

            // adopt前に既存JARファイルのパスを記録（後で削除するため）
            val oldJarFile = findJarForPlugin(unmanagedName)?.first

            progressCallback?.invoke("<gray>[$repoName] ダウンロード中...")

            // addWithDependenciesを呼び出してプラグインを追加
            addWithDependencies(
                PluginName(repoName),
                versionSpecifier,
                includeSoftDependencies
            ).fold(
                { error ->
                    // エラーが発生した場合
                    failedPlugins[repoName] = error.message
                },
                { result ->
                    // 成功した場合、結果を集約
                    adoptedPlugins.addAll(result.addedPlugins)
                    notFoundDependencies.addAll(result.notFoundPlugins)
                    failedPlugins.putAll(result.failedPlugins)

                    // インストール結果を進捗表示
                    result.addedPlugins.forEach { addResult ->
                        val depLabel = if (addResult.isDependency) "[依存] " else ""
                        // install()内で削除されたファイルの表示
                        addResult.installResult.removed?.let { removed ->
                            progressCallback?.invoke("<red>[$repoName] ${depLabel}削除: ${removed.name} ${removed.version}")
                        }
                        // インストールされた新ファイルの表示
                        val installed = addResult.installResult.installed
                        progressCallback?.invoke("<green>[$repoName] ${depLabel}インストール: ${installed.name} ${installed.currentVersion}")
                    }

                    // adopt成功かつメインプラグインが失敗していない場合のみpin情報を記録
                    if (pinResult is VersionPinResult.Pinned && !result.failedPlugins.containsKey(repoName)) {
                        pinnedPlugins.add(repoName)
                        pinResult.hashWarning?.let { hashMismatchWarnings[repoName] = it }
                    }

                    // 古いJARファイルを削除（新しいファイルと異なる場合のみ）
                    if (oldJarFile != null && oldJarFile.exists() && !result.failedPlugins.containsKey(repoName)) {
                        // メタデータから新しいファイル名を取得して比較
                        val newFileName = metadataManager.loadMetadata(repoName).getOrNull()
                            ?.mpmInfo?.download?.fileName
                        // 古いファイルと新しいファイルが異なる場合に削除（同名の場合はinstall()で上書き済み）
                        if (newFileName == null || oldJarFile.name != newFileName) {
                            try {
                                oldJarFile.delete()
                                progressCallback?.invoke("<red>[$repoName] 旧ファイル削除: ${oldJarFile.name}")
                            } catch (e: Exception) {
                                plugin.logger.warning("[$repoName] 旧ファイル削除に失敗: ${oldJarFile.name} (${e.message})")
                            }
                        }
                    }
                }
            )
        }

        return AdoptResult(
            adoptedPlugins = adoptedPlugins,
            skippedPlugins = skippedPlugins,
            failedPlugins = failedPlugins,
            notFoundDependencies = notFoundDependencies.distinct(),
            pinnedPlugins = pinnedPlugins,
            hashMismatchWarnings = hashMismatchWarnings,
            versionMismatchPlugins = versionMismatchPlugins
        ).right()
    }

    /**
     * --pin指定時のバージョン解決を行う
     *
     * リポジトリ情報の取得からJARバージョン検出までを行い、結果を返す。
     *
     * @param unmanagedName mpm.json上のプラグイン名
     * @param repoName リポジトリ上のプラグイン名
     * @return VersionPinResult（Pinned, FallbackToLatest, or Error）
     */
    private suspend fun resolveVersionForPin(
        unmanagedName: String,
        repoName: String,
        progressCallback: ((String) -> Unit)? = null
    ): VersionPinResult {
        // リポジトリファイルからURLデータを作成
        val repositoryFile = repositoryManager.getRepositoryFile(repoName)
        val firstRepository = repositoryFile?.repositories?.firstOrNull()
        val urlData = firstRepository?.let { createUrlData(it) }

        if (urlData == null) {
            return VersionPinResult.FallbackToLatest("リポジトリ情報が取得できません")
        }

        // JARからバージョンを検出してリポジトリで検索
        return resolveCurrentVersionFromJar(repoName, unmanagedName, urlData, progressCallback)
    }
}