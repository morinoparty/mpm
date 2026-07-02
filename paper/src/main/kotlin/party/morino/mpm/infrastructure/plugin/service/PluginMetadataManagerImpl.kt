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
import party.morino.mpm.api.domain.plugin.dto.ManagedPluginDto
import party.morino.mpm.api.domain.plugin.dto.MetadataDownloadInfoDto
import party.morino.mpm.api.domain.plugin.dto.MpmInfoDto
import party.morino.mpm.api.domain.plugin.dto.PluginInfoDto
import party.morino.mpm.api.domain.plugin.dto.RepositoryInfo
import party.morino.mpm.api.domain.plugin.dto.settings.PluginSettings
import party.morino.mpm.api.domain.plugin.dto.version.HistoryEntryDto
import party.morino.mpm.api.domain.plugin.dto.version.VersionDetailDto
import party.morino.mpm.api.domain.plugin.dto.version.VersionManagementDto
import party.morino.mpm.api.domain.plugin.model.VersionDetail
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

    /**
     * プラグイン名をメタデータファイル名として安全に使えるか検証する
     *
     * プラグイン名は悪意あるリポジトリファイル経由で外部から渡されうるため、
     * パス区切り・親ディレクトリ参照・ドライブ指定(`:`)・制御文字を含む名前を
     * 拒否してパストラバーサルを防ぐ。これは高速な一次フィルタであり、
     * 最終的な防御は [resolveMetadataFile] の正規化パス検証で行う。
     *
     * @param pluginName 検証対象のプラグイン名
     * @return 安全な場合は名前自身、不正な場合はエラーメッセージ
     */
    private fun sanitizePluginName(pluginName: String): Either<String, String> {
        // 空文字・空白のみの名前はファイル名として不正
        if (pluginName.isBlank()) {
            return "プラグイン名が空です".left()
        }
        // パス区切り文字・親ディレクトリ参照・ドライブ指定・制御文字を含む名前は拒否する
        // ':' を弾くことで Windows のドライブ修飾名(例: "C:evil")も防ぐ
        if (pluginName.contains("..") ||
            pluginName.contains('/') ||
            pluginName.contains('\\') ||
            pluginName.contains(':') ||
            pluginName.any { it.isISOControl() }
        ) {
            return "不正なプラグイン名です: $pluginName".left()
        }
        return pluginName.right()
    }

    /**
     * メタデータファイルのパスを安全に解決する
     *
     * 名前のサニタイズに加え、正規化（canonical）したパスが必ず metadata
     * ディレクトリ直下を指すことを検証する。これによりOS依存のパス解釈の
     * 違いに関わらずディレクトリ外への読み書きを防ぐ（パストラバーサル最終防御）。
     *
     * @param metadataDir metadataディレクトリ
     * @param pluginName プラグイン名
     * @return 安全に解決できた場合はFile、不正な場合はエラーメッセージ
     */
    private fun resolveMetadataFile(
        metadataDir: File,
        pluginName: String
    ): Either<String, File> {
        val safeName = sanitizePluginName(pluginName).getOrElse { return it.left() }
        val metadataFile = File(metadataDir, "$safeName.yaml")

        // 正規化後のパスが metadata ディレクトリ直下を指すか検証する
        // canonicalFileの解決に失敗した場合も安全側に倒して拒否する
        val withinDir =
            runCatching {
                metadataFile.canonicalFile.parentFile == metadataDir.canonicalFile
            }.getOrElse { false }
        if (!withinDir) {
            return "不正なプラグイン名です: $pluginName".left()
        }
        return metadataFile.right()
    }

    override suspend fun createMetadata(
        pluginName: String,
        repository: RepositoryConfig,
        versionData: VersionData,
        action: String,
        channel: String?
    ): Either<String, ManagedPluginDto> {
        // プラグイン名を検証（不正な名前は早期に弾く）
        val safeName = sanitizePluginName(pluginName).getOrElse { return it.left() }

        // 実効パターンを決定: チャンネル固有のversionModifier > ルートのversionPattern > デフォルト
        // これにより CarbonChat の beta チャンネルのような、チャンネルごとに
        // 異なる書式のバージョン列を正規化できる
        val effectivePattern = repository.effectiveVersionPattern(channel)

        // バージョンを正規化（共通ロジックを使用）
        val normalizedVersion = VersionDetail.normalizeWithPattern(versionData.version, effectivePattern)

        // 現在時刻を取得
        val now = Instant.now().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)

        // メタデータを作成
        val metadata =
            ManagedPluginDto(
                pluginInfo =
                    PluginInfoDto(
                        name = safeName,
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
                        versionPattern = effectivePattern,
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

        // バージョンを正規化（共通ロジックを使用）
        val versionPattern = existingMetadata.mpmInfo.versionPattern
        val normalizedCurrentVersion = VersionDetail.normalizeWithPattern(versionData.version, versionPattern)
        val normalizedLatestVersion = VersionDetail.normalizeWithPattern(latestVersionData.version, versionPattern)

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
        // メタデータディレクトリを取得し、安全なファイルパスを解決（パストラバーサル防止）
        val metadataDir = pluginDirectory.getMetadataDirectory()
        val metadataFile = resolveMetadataFile(metadataDir, pluginName).getOrElse { return it.left() }

        // ファイルが存在しない場合はエラー
        if (!metadataFile.exists()) {
            return "メタデータファイルが見つかりません: ${metadataFile.name}".left()
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

        // 安全なファイルパスを解決（パストラバーサル防止）
        val metadataFile = resolveMetadataFile(metadataDir, pluginName).getOrElse { return it.left() }

        // メタデータをYAML形式で保存
        return try {
            val yamlString = Yaml.default.encodeToString(ManagedPluginDto.serializer(), metadata)
            metadataFile.writeText(yamlString)
            Unit.right()
        } catch (e: Exception) {
            "メタデータの保存に失敗しました: ${e.message}".left()
        }
    }

    override fun deleteMetadata(pluginName: String): Either<String, Unit> {
        // メタデータディレクトリを取得し、安全なファイルパスを解決（パストラバーサル防止）
        val metadataDir = pluginDirectory.getMetadataDirectory()
        val metadataFile = resolveMetadataFile(metadataDir, pluginName).getOrElse { return it.left() }

        return try {
            if (metadataFile.exists() && !metadataFile.delete()) {
                return "メタデータファイルの削除に失敗しました: ${metadataFile.name}".left()
            }
            Unit.right()
        } catch (e: Exception) {
            "メタデータの削除に失敗しました: ${e.message}".left()
        }
    }
}