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
import party.morino.mpm.api.domain.migration.SchemaVersions
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
import party.morino.mpm.infrastructure.migration.AtomicFileWriter
import party.morino.mpm.infrastructure.migration.SchemaVersionGuard
import java.io.File
import java.nio.file.Files
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
    companion object {
        // 読み込めなくなったメタデータの退避先に付与する拡張子
        private const val QUARANTINE_SUFFIX = ".corrupt"

        // 退避先の連番の上限。これを超えたら退避先を作らずエラーにする（原本は消さない）
        private const val MAX_QUARANTINE_INDEX = 99
    }

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
    ): Either<String, File> = resolveInMetadataDir(metadataDir, pluginName) { "$it.yaml" }

    /**
     * metadataディレクトリ直下のファイルパスを安全に解決する
     *
     * [resolveMetadataFile] と退避先（`.corrupt`）の解決で同じ防御を使い回すための共通処理。
     * 退避先にもサニタイズと正規化パス検証を必ず通すことで、防御を迂回する経路を作らない。
     *
     * @param metadataDir metadataディレクトリ
     * @param pluginName プラグイン名
     * @param fileName サニタイズ済みの名前から実ファイル名を組み立てる関数
     * @return 安全に解決できた場合はFile、不正な場合はエラーメッセージ
     */
    private fun resolveInMetadataDir(
        metadataDir: File,
        pluginName: String,
        fileName: (String) -> String
    ): Either<String, File> {
        val safeName = sanitizePluginName(pluginName).getOrElse { return it.left() }
        val resolved = File(metadataDir, fileName(safeName))

        // 正規化後のパスが metadata ディレクトリ直下を指すか検証する
        // canonicalFileの解決に失敗した場合も安全側に倒して拒否する
        val withinDir =
            runCatching {
                resolved.canonicalFile.parentFile == metadataDir.canonicalFile
            }.getOrElse { false }
        if (!withinDir) {
            return "不正なプラグイン名です: $pluginName".left()
        }
        return resolved.right()
    }

    /**
     * まだ使われていない退避先ファイルを決める
     *
     * `<名前>.yaml.corrupt` から順に、既存ファイルとぶつからない連番を探す。
     * 上書きしないのは、過去に退避した原本（復旧の唯一の手がかり）を失わないため。
     *
     * @param metadataDir metadataディレクトリ
     * @param pluginName プラグイン名
     * @return 空いている退避先、見つからない場合はエラーメッセージ
     */
    private fun findQuarantineDestination(
        metadataDir: File,
        pluginName: String
    ): Either<String, File> {
        for (index in 0..MAX_QUARANTINE_INDEX) {
            // 0番目は連番なしの `.corrupt`、以降は `.corrupt.1` のように連番を付ける
            val suffix = if (index == 0) "" else ".$index"
            val candidate =
                resolveInMetadataDir(metadataDir, pluginName) {
                    "$it.yaml$QUARANTINE_SUFFIX$suffix"
                }.getOrElse { return it.left() }
            if (!candidate.exists()) {
                return candidate.right()
            }
        }
        return (
            "退避先のファイル名が枯渇しました: $pluginName.yaml$QUARANTINE_SUFFIX ～ " +
                "$pluginName.yaml$QUARANTINE_SUFFIX.$MAX_QUARANTINE_INDEX"
        ).left()
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

    /**
     * メタデータを metadata/xxx.yaml に保存する
     *
     * ディスク上のファイルが現行スキーマ版数より新しい場合は、書き込むと
     * ダウングレードになってしまうため保存せずにエラーを返す。
     *
     * @param pluginName プラグイン名
     * @param metadata 保存するメタデータ
     * @return 成功した場合はUnit、失敗した場合は理由
     */
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

        // 未来版数のファイルを巻き戻さないためのガード（書き込み前に必ず判定する）
        SchemaVersionGuard.ensureYamlWritable(metadataFile).onLeft { return it.left() }

        // 書き込み時は常に現行スキーマ版数をスタンプする
        // （マイグレート済みの metadata が保存のたびにレガシー版数へ巻き戻るのを防ぐ）
        val stamped = metadata.copy(schemaVersion = SchemaVersions.CURRENT)

        // シリアライズ失敗はファイルに触れる前に弾く（Either を返す契約のため例外を漏らさない）
        val yamlString =
            runCatching { Yaml.default.encodeToString(ManagedPluginDto.serializer(), stamped) }
                .getOrElse { return "メタデータの保存に失敗しました: ${it.message}".left() }

        // 一時ファイル経由で置換する。直接 writeText すると書き込み途中のクラッシュで
        // 半端なYAMLが残り、次回の読み込みが「破損」と判定して退避＋作り直しに進んでしまう。
        // その作り直しでは lock などの設定が失われるため、他の永続化処理と同じく原子的に書き換える。
        return AtomicFileWriter.write(metadataFile, yamlString)
    }

    /**
     * メタデータファイルを置き換え（上書き・退避して作り直し）てよいかを判定する
     *
     * ディスク上のファイルを読むだけで、いかなる副作用も持たない。
     * ダウンロードやJARの差し替え、イベント発火といった破壊的・不可逆な操作に入る前に呼び、
     * 中止すべき場合は何も壊していない段階で引き返すために使う。
     *
     * @param pluginName プラグイン名
     * @return 置き換えてよい場合はUnit、未来のスキーマ版数のため中止すべき場合はその理由
     */
    override fun ensureMetadataReplaceable(pluginName: String): Either<String, Unit> {
        // 判定対象の特定にも通常の読み書きと同じパス検証を通す（パストラバーサル防止）
        val metadataDir = pluginDirectory.getMetadataDirectory()
        val metadataFile = resolveMetadataFile(metadataDir, pluginName).getOrElse { return it.left() }

        // 「上書きしてよいか」と「退避して作り直してよいか」は同じ判定でよい。
        // どちらも現行版数のファイルで既存の内容を実質的に置き換える破壊的操作であり、
        // 未来版数のファイルに対してだけ拒否したいという条件が一致するため。
        return SchemaVersionGuard.ensureYamlWritable(metadataFile)
    }

    /**
     * 読み込めなくなったメタデータファイルを退避（隔離）する
     *
     * 移動は同一ディレクトリ内のリネームであり、失敗しても原本はその場に残る。
     * 退避先は必ず未使用の名前を選ぶため、[Files.move] に REPLACE_EXISTING は渡さない。
     *
     * @param pluginName プラグイン名
     * @return 退避した場合はその退避先ファイル、対象ファイルが存在しない場合はnull、失敗時はエラーメッセージ
     */
    override fun quarantineMetadata(pluginName: String): Either<String, File?> {
        // 退避元も通常の読み書きと同じパス検証を通す（パストラバーサル防止）
        val metadataDir = pluginDirectory.getMetadataDirectory()
        val metadataFile = resolveMetadataFile(metadataDir, pluginName).getOrElse { return it.left() }

        // そもそもファイルが無ければ退避するものは無い（新規インストールの通常経路）
        if (!metadataFile.exists()) {
            return null.right()
        }

        // 未来版数のファイルは「破損」ではなく「このmpmでは解釈できないだけ」なので退避しない。
        // 退避してしまうと原本が消え、続く作り直しで SchemaVersionGuard が
        // 「新規作成」としか見えなくなり、ダウングレード防止が迂回される。
        // 呼び出し側は事前に ensureMetadataReplaceable で中止できるが、
        // 経路の増減に関わらず必ず守られるようここでも判定する。
        SchemaVersionGuard.ensureYamlWritable(metadataFile).onLeft { return it.left() }

        val destination = findQuarantineDestination(metadataDir, pluginName).getOrElse { return it.left() }

        return try {
            Files.move(metadataFile.toPath(), destination.toPath())
            destination.right()
        } catch (e: Exception) {
            "メタデータの退避に失敗しました: ${e.message}".left()
        }
    }

    /**
     * 退避したメタデータファイルを元の場所へ戻す
     *
     * 退避と同じディレクトリ内のリネームであり、失敗しても退避先の内容はその場に残る。
     * 元のパスを上書きしないため [Files.move] に REPLACE_EXISTING は渡さない。
     *
     * @param pluginName プラグイン名
     * @param quarantinedFile [quarantineMetadata] が返した退避先ファイル
     * @return 戻せた場合はUnit、失敗時はエラーメッセージ
     */
    override fun restoreQuarantinedMetadata(
        pluginName: String,
        quarantinedFile: File
    ): Either<String, Unit> {
        // 戻し先も通常の読み書きと同じパス検証を通す（パストラバーサル防止）
        val metadataDir = pluginDirectory.getMetadataDirectory()
        val metadataFile = resolveMetadataFile(metadataDir, pluginName).getOrElse { return it.left() }

        // 退避先として渡されたパスも metadata ディレクトリ直下であることを検証する。
        // 呼び出し側が持ち回った File をそのまま信用すると、防御を迂回する経路になりうる
        val withinDir =
            runCatching {
                quarantinedFile.canonicalFile.parentFile == metadataDir.canonicalFile
            }.getOrElse { false }
        if (!withinDir) {
            return "不正な退避先です: ${quarantinedFile.path}".left()
        }

        // ディレクトリ境界だけでは「このプラグインの退避成果物か」までは担保できない。
        // 別プラグインの健全な `Other.yaml` を渡されると、それを
        // `<このプラグイン名>.yaml` へ移動して Other のメタデータを無音で失わせてしまう。
        // 退避先の命名規則（[findQuarantineDestination]）に一致することまで検証する
        val safeName = sanitizePluginName(pluginName).getOrElse { return it.left() }
        if (!quarantinedFile.name.startsWith("$safeName.yaml$QUARANTINE_SUFFIX")) {
            return "不正な退避先です: ${quarantinedFile.path}".left()
        }

        // 退避先が無ければ戻すものが無い（既に手動で戻された場合など）
        if (!quarantinedFile.exists()) {
            return "退避したメタデータが見つかりません: ${quarantinedFile.name}".left()
        }

        // 元のパスに何かある場合は上書きしない。
        // 読めないファイルで有効なファイルを潰す方が被害が大きいため、失敗として報告する
        if (metadataFile.exists()) {
            return "元のメタデータファイルが既に存在するため戻せません: ${metadataFile.name}".left()
        }

        return try {
            Files.move(quarantinedFile.toPath(), metadataFile.toPath())
            Unit.right()
        } catch (e: Exception) {
            "メタデータの復元に失敗しました: ${e.message}".left()
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