/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.plugin.service

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.charleskorn.kaml.Yaml
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.downloader.model.RepositoryType
import party.morino.mpm.api.domain.downloader.model.VersionData
import party.morino.mpm.api.domain.plugin.dto.HistoryEntryDto
import party.morino.mpm.api.domain.plugin.dto.ManagedPluginDto
import party.morino.mpm.api.domain.plugin.dto.MetadataDownloadInfoDto
import party.morino.mpm.api.domain.plugin.dto.MpmInfoDto
import party.morino.mpm.api.domain.plugin.dto.PluginInfoDto
import party.morino.mpm.api.domain.plugin.dto.PluginSettings
import party.morino.mpm.api.domain.plugin.dto.RepositoryInfo
import party.morino.mpm.api.domain.plugin.dto.VersionDetailDto
import party.morino.mpm.api.domain.plugin.dto.VersionManagementDto
import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import party.morino.mpm.api.domain.repository.RepositoryConfig
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * プラグインメタデータ管理の実装クラス
 * metadata/xxx.yamlファイルの操作を担当する
 **/

class PluginMetadataManagerImpl :
    PluginMetadataManager,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()

    override suspend fun createMetadata(
        pluginName: String,
        repository: RepositoryConfig,
        versionData: VersionData,
        action: String
    ): Either<String, ManagedPluginDto> {
        // バージョンを正規化
        val versionPattern = repository.versionPattern
        val normalizedVersion =
            if (versionPattern != null) {
                val versionRegex = Regex(versionPattern)
                versionRegex.find(versionData.version)?.value ?: versionData.version
            } else {
                versionData.version
            }

        // 現在時刻を取得
        val now = Instant.now().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)

        // メタデータを作成
        val metadata =
            ManagedPluginDto(
                pluginInfo =
                    PluginInfoDto(
                        name = pluginName,
                        version = normalizedVersion
                    ),
                mpmInfo =
                    MpmInfoDto(
                        repository =
                            RepositoryInfo(
                                type = RepositoryType.valueOf(repository.type.uppercase()),
                                id = repository.repositoryId
                            ),
                        version =
                            VersionManagementDto(
                                current =
                                    VersionDetailDto(
                                        raw = versionData.version,
                                        normalized = normalizedVersion
                                    ),
                                latest =
                                    VersionDetailDto(
                                        raw = versionData.version,
                                        normalized = normalizedVersion
                                    ),
                                lastChecked = now
                            ),
                        download =
                            MetadataDownloadInfoDto(
                                downloadId = versionData.downloadId
                            ),
                        settings =
                            PluginSettings(
                                lock = false,
                                autoUpdate = false
                            ),
                        history =
                            listOf(
                                HistoryEntryDto(
                                    version = normalizedVersion,
                                    installedAt = now,
                                    action = action
                                )
                            ),
                        versionPattern = repository.versionPattern,
                        fileNamePattern = repository.fileNamePattern,
                        fileNameTemplate = repository.fileNameTemplate
                    )
            )

        return metadata.right()
    }

    override suspend fun updateMetadata(
        pluginName: String,
        versionData: VersionData,
        latestVersionData: VersionData,
        action: String
    ): Either<String, ManagedPluginDto> {
        // 既存のメタデータを読み込む
        val existingMetadata = loadMetadata(pluginName).getOrElse { return it.left() }

        // バージョンを正規化
        val versionPattern = existingMetadata.mpmInfo.versionPattern
        val normalizedCurrentVersion =
            if (versionPattern != null) {
                val versionRegex = Regex(versionPattern)
                versionRegex.find(versionData.version)?.value ?: versionData.version
            } else {
                versionData.version
            }

        val normalizedLatestVersion =
            if (versionPattern != null) {
                val versionRegex = Regex(versionPattern)
                versionRegex.find(latestVersionData.version)?.value ?: latestVersionData.version
            } else {
                latestVersionData.version
            }

        // 現在時刻を取得
        val now = Instant.now().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)

        // 履歴に新しいエントリを追加
        val newHistory =
            existingMetadata.mpmInfo.history +
                HistoryEntryDto(
                    version = normalizedCurrentVersion,
                    installedAt = now,
                    action = action
                )

        // メタデータを更新
        val updatedMetadata =
            existingMetadata.copy(
                pluginInfo = existingMetadata.pluginInfo.copy(version = normalizedCurrentVersion),
                mpmInfo =
                    existingMetadata.mpmInfo.copy(
                        version =
                            existingMetadata.mpmInfo.version.copy(
                                current =
                                    VersionDetailDto(
                                        raw = versionData.version,
                                        normalized = normalizedCurrentVersion
                                    ),
                                latest =
                                    VersionDetailDto(
                                        raw = latestVersionData.version,
                                        normalized = normalizedLatestVersion
                                    ),
                                lastChecked = now
                            ),
                        download =
                            existingMetadata.mpmInfo.download.copy(
                                downloadId = versionData.downloadId
                            ),
                        history = newHistory
                    )
            )

        return updatedMetadata.right()
    }

    override fun loadMetadata(pluginName: String): Either<String, ManagedPluginDto> {
        // メタデータディレクトリを取得
        val metadataDir = pluginDirectory.getMetadataDirectory()
        val metadataFile = File(metadataDir, "$pluginName.yaml")

        // ファイルが存在しない場合はエラー
        if (!metadataFile.exists()) {
            return "メタデータファイルが見つかりません: $pluginName.yaml".left()
        }

        // メタデータを読み込む
        return try {
            val yamlString = metadataFile.readText()
            val metadata = Yaml.default.decodeFromString(ManagedPluginDto.serializer(), yamlString)
            metadata.right()
        } catch (e: Exception) {
            "メタデータの読み込みに失敗しました: ${e.message}".left()
        }
    }

    override fun saveMetadata(
        pluginName: String,
        metadata: ManagedPluginDto
    ): Either<String, Unit> {
        // メタデータディレクトリを取得（存在しなければ作成）
        val metadataDir = pluginDirectory.getMetadataDirectory()
        if (!metadataDir.exists()) {
            metadataDir.mkdirs()
        }

        // メタデータファイルのパスを作成
        val metadataFile = File(metadataDir, "$pluginName.yaml")

        // メタデータをYAML形式で保存
        return try {
            val yamlString = Yaml.default.encodeToString(ManagedPluginDto.serializer(), metadata)
            metadataFile.writeText(yamlString)
            Unit.right()
        } catch (e: Exception) {
            "メタデータの保存に失敗しました: ${e.message}".left()
        }
    }
}
