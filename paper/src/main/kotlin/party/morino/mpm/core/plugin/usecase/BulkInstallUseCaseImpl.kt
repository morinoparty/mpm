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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.config.PluginDirectory
import party.morino.mpm.api.config.plugin.MpmConfig
import party.morino.mpm.api.config.plugin.VersionSpecifierParser
import party.morino.mpm.api.config.plugin.topologicalSortPlugins
import party.morino.mpm.api.config.plugin.validateSyncDependencies
import party.morino.mpm.api.core.plugin.BulkInstallResult
import party.morino.mpm.api.core.plugin.BulkInstallUseCase
import party.morino.mpm.api.core.plugin.DownloaderRepository
import party.morino.mpm.api.core.plugin.InstallResult
import party.morino.mpm.api.core.plugin.PluginInstallInfo
import party.morino.mpm.api.core.plugin.PluginMetadataManager
import party.morino.mpm.api.core.plugin.PluginRemovalInfo
import party.morino.mpm.api.core.repository.RepositoryManager
import party.morino.mpm.api.model.repository.UrlData
import party.morino.mpm.utils.DataClassReplacer.replaceTemplate
import party.morino.mpm.utils.Utils
import java.io.File

/**
 * 一括インストールユースケースの実装
 */
class BulkInstallUseCaseImpl :
    BulkInstallUseCase,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()
    private val metadataManager: PluginMetadataManager by inject()
    private val repositorySourceManager: RepositoryManager by inject()
    private val downloaderRepository: DownloaderRepository by inject()

    override suspend fun installAll(): Either<String, BulkInstallResult> {
        // mpm.jsonを読み込む
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "mpm.json")

        if (!configFile.exists()) {
            return "mpm.jsonが存在しません。先に 'mpm init' を実行してください。".left()
        }

        val mpmConfig =
            try {
                val jsonString = configFile.readText()
                Utils.json.decodeFromString<MpmConfig>(jsonString)
            } catch (e: Exception) {
                return "mpm.jsonの読み込みに失敗しました: ${e.message}".left()
            }

        // Sync依存関係のバリデーション
        mpmConfig.validateSyncDependencies().onLeft { error ->
            return error.toMessage().left()
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
            val metadataResult = metadataManager.loadMetadata(pluginName)

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
            installSinglePlugin(pluginName, versionToInstall).fold(
                { failed[pluginName] = it },
                { result ->
                    installed.add(result.installed)
                    result.removed?.let { removed.add(it) }
                    resolvedVersions[pluginName] = result.installed.currentVersion
                }
            )
        }

        return BulkInstallResult(installed = installed, removed = removed, failed = failed).right()
    }

    /**
     * 単一のプラグインをインストールする内部関数
     */
    private suspend fun installSinglePlugin(
        pluginName: String,
        expectedVersion: String
    ): Either<String, InstallResult> {
        // リポジトリファイルを取得
        val repositoryFile =
            repositorySourceManager.getRepositoryFile(pluginName)
                ?: return "リポジトリファイルが見つかりません: $pluginName".left()

        val firstRepository =
            repositoryFile.repositories.firstOrNull()
                ?: return "リポジトリ設定が見つかりません: $pluginName".left()

        // UrlDataを作成
        val urlData =
            when (firstRepository.type.lowercase()) {
                "github" -> {
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
        val latestVersionData =
            try {
                downloaderRepository.getLatestVersion(urlData)
            } catch (e: Exception) {
                return "バージョン情報の取得に失敗しました: ${e.message}".left()
            }

        // 指定バージョンを取得
        val versionData =
            if (expectedVersion == "latest") {
                // 最新バージョンをそのまま使用
                latestVersionData
            } else {
                // 特定のバージョンを取得（正しいdownloadIdを含む）
                try {
                    downloaderRepository.getVersionByName(urlData, expectedVersion)
                } catch (e: Exception) {
                    return "指定されたバージョン '$expectedVersion' の取得に失敗しました: ${e.message}".left()
                }
            }

        // メタデータが存在するか確認し、更新または作成
        val metadata =
            metadataManager.loadMetadata(pluginName).fold(
                // メタデータが存在しない場合は新規作成
                {
                    metadataManager
                        .createMetadata(pluginName, firstRepository, versionData, "install")
                        .getOrElse { return it.left() }
                },
                // メタデータが存在する場合は更新
                {
                    metadataManager
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
                // 削除されたファイル情報を記録（既存のメタデータから取得）
                metadataManager.loadMetadata(pluginName).onRight { existingMetadata ->
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
        metadataManager.saveMetadata(pluginName, updatedMetadata).getOrElse { return it.left() }

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
                mpmInfo =
                    MpmInfo(
                        version = MpmInfoVersion(current = CurrentVersion(normalized = versionString))
                    )
            )

        return template.replaceTemplate(data)
    }

    /**
     * バージョン指定文字列を実際のバージョンに解決する
     * @param pluginName プラグイン名
     * @param expected バージョン指定文字列
     * @param resolved 解決済みバージョンのマップ
     * @return 解決されたバージョン文字列
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
                metadataManager.loadMetadata(pluginName).fold(
                    { expected },
                    { it.mpmInfo.version.current.raw }
                )
            else -> expected
        }
    }
}