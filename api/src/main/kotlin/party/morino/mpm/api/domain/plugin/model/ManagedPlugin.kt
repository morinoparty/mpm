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

package party.morino.mpm.api.domain.plugin.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import party.morino.mpm.api.domain.downloader.model.RepositoryType
import party.morino.mpm.api.domain.plugin.dto.ManagedPluginDto
import party.morino.mpm.api.domain.plugin.dto.MetadataDownloadInfoDto
import party.morino.mpm.api.domain.plugin.dto.MpmInfoDto
import party.morino.mpm.api.domain.plugin.dto.PluginInfoDto
import party.morino.mpm.api.domain.plugin.dto.RepositoryInfo
import party.morino.mpm.api.domain.plugin.dto.settings.PluginSettings
import party.morino.mpm.api.domain.plugin.dto.version.HistoryEntryDto
import party.morino.mpm.api.domain.plugin.dto.version.VersionDetailDto
import party.morino.mpm.api.domain.plugin.dto.version.VersionManagementDto
import party.morino.mpm.api.shared.error.MpmError

/**
 * MPMで管理されるプラグインを表すドメインエンティティ
 *
 * プラグインメタデータを管理し、ビジネスロジックを持つリッチドメインモデル
 * MpmProjectとは別のアグリゲートとして機能する
 */
class ManagedPlugin private constructor(
    private val pluginInfo: PluginInfo,
    private val mpmInfo: MpmInfoDto,
    /**
     * エントリの由来（管理下 / 管理外 / メタデータ読み込み失敗）
     *
     * 一覧取得時に「mpm.jsonに未登録」と「登録済みだがメタデータを読めなかった」を
     * 呼び出し側が区別するために保持する。
     */
    val status: PluginEntryStatus = PluginEntryStatus.MANAGED
) {
    /**
     * プラグイン名（PluginInfo由来の単一ソース）
     */
    val name: PluginName get() = PluginName(pluginInfo.name)

    /**
     * プラグインのバージョン文字列
     */
    val version: String get() = pluginInfo.version

    /**
     * プラグインの説明
     */
    val description: String? get() = pluginInfo.description

    /**
     * プラグインの作者
     */
    val author: String? get() = pluginInfo.author

    /**
     * プラグインのウェブサイト
     */
    val website: String? get() = pluginInfo.website

    /**
     * ロック状態（PluginSettingsに集約）
     */
    val isLocked: Boolean get() = mpmInfo.settings.lock ?: false

    /**
     * リポジトリ情報
     */
    val repository: RepositoryInfo get() = mpmInfo.repository

    /**
     * 現在のバージョン詳細
     */
    val currentVersion: VersionDetail
        get() =
            VersionDetail(
                raw = mpmInfo.version.current.raw,
                normalized = mpmInfo.version.current.normalized
            )

    /**
     * 最新のバージョン詳細
     */
    val latestVersion: VersionDetail
        get() =
            VersionDetail(
                raw = mpmInfo.version.latest.raw,
                normalized = mpmInfo.version.latest.normalized
            )

    /**
     * ダウンロードID
     */
    val downloadId: String get() = mpmInfo.download.downloadId

    /**
     * ファイル名
     */
    val fileName: String? get() = mpmInfo.download.fileName

    /**
     * ダウンロードURL
     */
    val downloadUrl: String? get() = mpmInfo.download.url

    /**
     * 最終チェック日時
     */
    val lastChecked: String get() = mpmInfo.version.lastChecked

    /**
     * インストール履歴
     */
    val history: List<HistoryEntry> get() =
        mpmInfo.history.map {
            HistoryEntry(version = it.version, installedAt = it.installedAt, action = it.action)
        }

    // ===== ドメインロジック =====

    /**
     * プラグインをロックする
     *
     * ロック済みの場合はエラーを返す
     */
    fun lock(): Either<MpmError, ManagedPlugin> {
        if (isLocked) {
            return MpmError.PluginError.Locked(name.value).left()
        }
        val newSettings = mpmInfo.settings.copy(lock = true)
        val newMpmInfo = mpmInfo.copy(settings = newSettings)
        return ManagedPlugin(pluginInfo, newMpmInfo, status).right()
    }

    /**
     * プラグインのロックを解除する
     *
     * ロックされていない場合はエラーを返す
     */
    fun unlock(): Either<MpmError, ManagedPlugin> {
        if (!isLocked) {
            return MpmError.PluginError.NotLocked(name.value).left()
        }
        val newSettings = mpmInfo.settings.copy(lock = false)
        val newMpmInfo = mpmInfo.copy(settings = newSettings)
        return ManagedPlugin(pluginInfo, newMpmInfo, status).right()
    }

    /**
     * 更新が必要かどうかを判定
     */
    fun isOutdated(): Boolean = currentVersion.normalized != latestVersion.normalized

    /**
     * 指定バージョンへの更新が可能かどうかを判定
     *
     * ロックされている場合は更新不可
     */
    fun canUpdate(newVersion: VersionDetail): Boolean {
        if (isLocked) return false
        return currentVersion.normalized != newVersion.normalized
    }

    /**
     * バージョンを更新した新しいインスタンスを返す
     *
     * @param newVersion 新しいバージョン
     * @param downloadId ダウンロードID
     * @param fileName ファイル名
     * @param downloadUrl ダウンロードURL
     * @param timestamp タイムスタンプ
     */
    fun withUpdatedVersion(
        newVersion: VersionDetail,
        downloadId: String,
        fileName: String?,
        downloadUrl: String?,
        timestamp: String
    ): ManagedPlugin {
        val newPluginInfo = pluginInfo.copy(version = newVersion.raw)
        val newVersionDto = VersionDetailDto(raw = newVersion.raw, normalized = newVersion.normalized)
        val newVersionManagement =
            mpmInfo.version.copy(
                current = newVersionDto,
                latest = newVersionDto,
                lastChecked = timestamp
            )
        val newDownload =
            mpmInfo.download.copy(
                downloadId = downloadId,
                fileName = fileName,
                url = downloadUrl
            )
        val newHistoryEntry =
            HistoryEntryDto(
                version = newVersion.raw,
                installedAt = timestamp,
                action = "update"
            )
        val newMpmInfo =
            mpmInfo.copy(
                version = newVersionManagement,
                download = newDownload,
                history = mpmInfo.history + newHistoryEntry
            )
        return ManagedPlugin(newPluginInfo, newMpmInfo, status)
    }

    /**
     * 履歴エントリを追加した新しいインスタンスを返す
     */
    fun withHistory(entry: HistoryEntry): ManagedPlugin {
        val entryDto =
            HistoryEntryDto(
                version = entry.version,
                installedAt = entry.installedAt,
                action = entry.action
            )
        val newMpmInfo = mpmInfo.copy(history = mpmInfo.history + entryDto)
        return ManagedPlugin(pluginInfo, newMpmInfo, status)
    }

    // ===== DTO変換 =====

    /**
     * DTOに変換（永続化用）
     */
    fun toDto(): ManagedPluginDto =
        ManagedPluginDto(
            pluginInfo =
                PluginInfoDto(
                    name = pluginInfo.name,
                    version = pluginInfo.version,
                    description = pluginInfo.description,
                    main = pluginInfo.main,
                    author = pluginInfo.author,
                    website = pluginInfo.website
                ),
            mpmInfo = mpmInfo
        )

    companion object {
        // mpm.jsonでunmanagedを表すバージョン文字列（MpmProjectのパースと同じ値）
        private const val UNMANAGED_VERSION = "unmanaged"

        // メタデータを読み込めなかった場合のバージョン表示
        private const val UNKNOWN_VERSION = "unknown"

        /**
         * DTOからエンティティを生成
         */
        fun fromDto(dto: ManagedPluginDto): ManagedPlugin {
            val pluginInfo =
                PluginInfo(
                    name = dto.pluginInfo.name,
                    version = dto.pluginInfo.version,
                    description = dto.pluginInfo.description,
                    main = dto.pluginInfo.main,
                    author = dto.pluginInfo.author,
                    website = dto.pluginInfo.website
                )
            return ManagedPlugin(pluginInfo, dto.mpmInfo)
        }

        /**
         * unmanagedプラグイン用のインスタンスを作成
         *
         * メタデータがないプラグイン向けに最小限の情報でインスタンスを生成する
         *
         * @param pluginName プラグイン名
         * @return ManagedPluginインスタンス（unmanaged状態）
         */
        fun createUnmanaged(pluginName: String): ManagedPlugin =
            createPlaceholder(pluginName, UNMANAGED_VERSION, PluginEntryStatus.UNMANAGED)

        /**
         * メタデータを読み込めなかった管理下プラグイン用のインスタンスを作成
         *
         * mpm.jsonには登録されているがmetadata/xxx.yamlを読めなかった場合に用いる。
         * 一覧から黙って除外すると「未登録」と区別できなくなるため、
         * [PluginEntryStatus.METADATA_UNAVAILABLE] を持つプレースホルダとして返す。
         *
         * @param pluginName プラグイン名
         * @return ManagedPluginインスタンス（メタデータ不明状態）
         */
        fun createMetadataUnavailable(pluginName: String): ManagedPlugin =
            createPlaceholder(pluginName, UNKNOWN_VERSION, PluginEntryStatus.METADATA_UNAVAILABLE)

        /**
         * メタデータを持たないプラグイン用のプレースホルダを生成する
         *
         * @param pluginName プラグイン名
         * @param versionSentinel バージョン欄に入れるセンチネル文字列
         * @param status エントリの状態
         */
        private fun createPlaceholder(
            pluginName: String,
            versionSentinel: String,
            status: PluginEntryStatus
        ): ManagedPlugin {
            val pluginInfo =
                PluginInfo(
                    name = pluginName,
                    version = versionSentinel,
                    description = null,
                    main = null,
                    author = null,
                    website = null
                )
            // メタデータを持たないプラグイン用のダミーMpmInfo
            // currentとlatestが同値になるためisOutdated()は常にfalseとなり、
            // OUTDATED/LOCKEDフィルタからは自然に除外される
            val sentinelVersion =
                VersionDetailDto(
                    raw = versionSentinel,
                    normalized = versionSentinel
                )
            val mpmInfo =
                MpmInfoDto(
                    repository =
                        RepositoryInfo(
                            type = RepositoryType.UNKNOWN,
                            id = ""
                        ),
                    version =
                        VersionManagementDto(
                            current = sentinelVersion,
                            latest = sentinelVersion,
                            lastChecked = ""
                        ),
                    download =
                        MetadataDownloadInfoDto(
                            downloadId = "",
                            fileName = null,
                            url = null
                        ),
                    settings = PluginSettings(),
                    history = emptyList()
                )
            return ManagedPlugin(pluginInfo, mpmInfo, status)
        }

        /**
         * 新規プラグインを作成
         */
        fun create(request: CreatePluginRequest): ManagedPlugin {
            val pluginInfo =
                PluginInfo(
                    name = request.name,
                    version = request.version,
                    description = request.description,
                    main = null,
                    author = request.author,
                    website = request.website
                )
            val versionDto =
                VersionDetailDto(
                    raw = request.versionDetail.raw,
                    normalized = request.versionDetail.normalized
                )
            val mpmInfo =
                MpmInfoDto(
                    repository = request.repository,
                    version =
                        VersionManagementDto(
                            current = versionDto,
                            latest = versionDto,
                            lastChecked = request.timestamp
                        ),
                    download =
                        MetadataDownloadInfoDto(
                            downloadId = request.downloadId,
                            fileName = request.fileName,
                            url = request.downloadUrl
                        ),
                    settings = PluginSettings(),
                    history =
                        listOf(
                            HistoryEntryDto(
                                version = request.version,
                                installedAt = request.timestamp,
                                action = "install"
                            )
                        )
                )
            return ManagedPlugin(pluginInfo, mpmInfo)
        }
    }

    // ===== 内部データクラス =====

    /**
     * プラグインの基本情報（内部用）
     */
    private data class PluginInfo(
        val name: String,
        val version: String,
        val description: String?,
        val main: String?,
        val author: String?,
        val website: String?
    )
}

/**
 * 履歴エントリ
 */
data class HistoryEntry(
    val version: String,
    val installedAt: String,
    val action: String
)

/**
 * プラグイン作成リクエスト
 */
data class CreatePluginRequest(
    val name: String,
    val version: String,
    val description: String?,
    val author: String?,
    val website: String?,
    val repository: RepositoryInfo,
    val versionDetail: VersionDetail,
    val downloadId: String,
    val fileName: String?,
    val downloadUrl: String?,
    val timestamp: String
)