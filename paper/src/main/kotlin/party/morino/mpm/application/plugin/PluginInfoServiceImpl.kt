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
import party.morino.mpm.api.application.model.OutdatedCheckResult
import party.morino.mpm.api.application.model.OutdatedInfo
import party.morino.mpm.api.application.model.PluginCheckError
import party.morino.mpm.api.application.model.PluginFilter
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.domain.downloader.DownloaderRepository
import party.morino.mpm.api.domain.downloader.model.UrlData
import party.morino.mpm.api.domain.plugin.model.ManagedPlugin
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.domain.plugin.model.PluginSpec
import party.morino.mpm.api.domain.plugin.model.VersionDetail
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import party.morino.mpm.api.domain.project.repository.ProjectRepository
import party.morino.mpm.api.domain.repository.RepositoryManager
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.api.shared.error.MpmError
import party.morino.mpm.event.PluginOutdatedEvent
import party.morino.mpm.utils.BukkitDispatcher

/**
 * プラグイン情報の取得を行うApplication Service実装
 *
 * UseCaseのロジックを直接実装
 */
class PluginInfoServiceImpl :
    PluginInfoService,
    KoinComponent {
    // Koinによる依存性注入
    private val projectRepository: ProjectRepository by inject()
    private val repositoryManager: RepositoryManager by inject()
    private val downloaderRepository: DownloaderRepository by inject()
    private val pluginMetadataManager: PluginMetadataManager by inject()
    private val plugin: JavaPlugin by inject()

    /**
     * プラグイン一覧を取得する
     *
     * mpm.jsonのプラグインリストとメタデータファイルを組み合わせて
     * ManagedPluginのリストを返す
     */
    override suspend fun list(filter: PluginFilter): List<ManagedPlugin> {
        // ProjectRepositoryを通じてプロジェクトを取得
        val project = projectRepository.find() ?: return emptyList()

        // 各プラグインのメタデータを読み込んでManagedPluginに変換
        val managedPlugins =
            project.plugins.mapNotNull { (pluginName, spec) ->
                // unmanagedの場合はスキップ（フィルタ次第で含める）
                val isUnmanaged = spec is PluginSpec.Unmanaged

                // フィルタに応じた処理
                when (filter) {
                    PluginFilter.UNMANAGED -> {
                        // unmanagedのみを対象
                        if (!isUnmanaged) return@mapNotNull null
                        // unmanagedプラグインはメタデータがないので、最小限のManagedPluginを作成
                        return@mapNotNull ManagedPlugin.createUnmanaged(pluginName.value)
                    }
                    PluginFilter.MANAGED -> {
                        // managedのみを対象
                        if (isUnmanaged) return@mapNotNull null
                    }
                    PluginFilter.ALL -> {
                        // unmanagedはスキップ（メタデータがないため）
                        if (isUnmanaged) return@mapNotNull null
                    }
                    PluginFilter.OUTDATED, PluginFilter.LOCKED -> {
                        // 後でフィルタするのでスキップしない
                        if (isUnmanaged) return@mapNotNull null
                    }
                }

                // メタデータを読み込む
                val metadataResult = pluginMetadataManager.loadMetadata(pluginName.value)
                val dto = metadataResult.getOrElse { return@mapNotNull null }

                // DTOからドメインエンティティに変換
                ManagedPlugin.fromDto(dto)
            }

        // フィルタに応じた絞り込み
        return when (filter) {
            PluginFilter.OUTDATED -> managedPlugins.filter { it.isOutdated() }
            PluginFilter.LOCKED -> managedPlugins.filter { it.isLocked }
            else -> managedPlugins
        }
    }

    /**
     * プラグインの利用可能なバージョン一覧を取得する
     *
     * PluginVersionsUseCaseImplから移行したロジック
     */
    override suspend fun getVersions(name: PluginName): Either<MpmError, List<VersionDetail>> {
        // ProjectRepositoryを通じてプロジェクトの存在を確認
        if (!projectRepository.exists()) {
            return MpmError.ProjectError.ConfigNotFound.left()
        }

        // リポジトリファイルを取得
        val repositoryFile =
            repositoryManager.getRepositoryFile(name.value)
                ?: return MpmError.PluginError.RepositoryNotFound(name.value).left()

        // リポジトリ設定から最初のリポジトリを取得
        val firstRepository =
            repositoryFile.repositories.firstOrNull()
                ?: return MpmError.PluginError.RepositoryNotFound(name.value).left()

        // RepositoryConfigからUrlDataを作成
        val urlData =
            createUrlData(firstRepository.type, firstRepository.repositoryId)
                ?: return MpmError.PluginError.UnsupportedRepository(firstRepository.type).left()

        // すべてのバージョンを取得
        val versions =
            try {
                downloaderRepository.getAllVersions(urlData)
            } catch (e: Exception) {
                return MpmError.PluginError
                    .VersionResolutionFailed(
                        name.value,
                        "バージョン情報の取得に失敗しました: ${e.message}"
                    ).left()
            }

        // versionPatternを取得（リポジトリ設定から）
        val versionPattern = firstRepository.versionPattern

        // バージョン番号のリストをVersionDetailに変換して返す
        return versions
            .map { versionData ->
                VersionDetail.fromRaw(versionData.version, versionPattern)
            }.right()
    }

    /**
     * 特定のプラグインの更新情報を確認する
     *
     * CheckOutdatedUseCaseImplから移行したロジック
     */
    override suspend fun checkOutdated(name: PluginName): Either<MpmError, OutdatedInfo?> {
        // ProjectRepositoryを通じてプロジェクトを取得（パースエラーも区別する）
        val project = projectRepository.findOrError().getOrElse { return it.left() }

        // プラグインが管理対象に含まれているか確認
        if (project.getPluginSpec(name) == null) {
            return MpmError.PluginError.NotManaged(name.value).left()
        }

        // メタデータを読み込む
        val metadata =
            pluginMetadataManager.loadMetadata(name.value).getOrElse {
                return MpmError.PluginError.MetadataNotFound(name.value).left()
            }

        // リポジトリファイルを取得
        val repositoryFile =
            repositoryManager.getRepositoryFile(name.value)
                ?: return MpmError.PluginError.RepositoryNotFound(name.value).left()

        // リポジトリ設定から最初のリポジトリを取得
        val firstRepository =
            repositoryFile.repositories.firstOrNull()
                ?: return MpmError.PluginError.RepositoryNotFound(name.value).left()

        // RepositoryConfigからUrlDataを作成
        val urlData =
            createUrlData(firstRepository.type, firstRepository.repositoryId)
                ?: return MpmError.PluginError.UnsupportedRepository(firstRepository.type).left()

        // tag:指定の場合は該当チャンネルの最新、それ以外は絶対的な最新を取得
        val pluginSpec = project.getPluginSpec(name)
        val versionSpecifier = (pluginSpec as? PluginSpec.Managed)?.versionRequirement
        val latestVersion =
            try {
                if (versionSpecifier is VersionSpecifier.Tag) {
                    downloaderRepository.getLatestVersionByTag(urlData, versionSpecifier.tag)
                        ?: return MpmError.PluginError
                            .VersionResolutionFailed(
                                name.value,
                                "tag '${versionSpecifier.tag}' に該当するバージョンが見つかりません"
                            ).left()
                } else {
                    downloaderRepository.getLatestVersion(urlData)
                }
            } catch (e: Exception) {
                return MpmError.PluginError
                    .VersionResolutionFailed(
                        name.value,
                        "最新バージョンの取得に失敗しました: ${e.message}"
                    ).left()
            }

        // 現在のバージョンと最新バージョンを正規化して比較
        val versionPattern = metadata.mpmInfo.versionPattern
        val currentNormalized = VersionDetail.fromRaw(metadata.mpmInfo.version.current.raw, versionPattern).normalized
        val latestNormalized = VersionDetail.fromRaw(latestVersion.version, versionPattern).normalized
        val currentVersion = metadata.mpmInfo.version.current.raw
        val needsUpdate = currentNormalized != latestNormalized

        // 更新が必要な場合はBukkitイベントを発火
        // PaperMCではイベントはメインスレッドで発火する必要があるため、BukkitDispatcherを使用
        if (needsUpdate) {
            BukkitDispatcher.callEventSync(
                plugin,
                PluginOutdatedEvent(
                    installedPlugin = InstalledPlugin(name.value),
                    currentVersion = currentVersion,
                    latestVersion = latestVersion.version
                )
            )
        }

        return OutdatedInfo(
            pluginName = name.value,
            currentVersion = currentVersion,
            latestVersion = latestVersion.version,
            needsUpdate = needsUpdate
        ).right()
    }

    /**
     * すべてのプラグインの更新情報を確認する
     *
     * CheckOutdatedUseCaseImplから移行したロジック
     */
    override suspend fun checkAllOutdated(): Either<MpmError, OutdatedCheckResult> {
        // ProjectRepositoryを通じてプロジェクトを取得（パースエラーも区別する）
        val project = projectRepository.findOrError().getOrElse { return it.left() }

        // すべての管理対象プラグインの更新情報を収集
        val outdatedInfoList = mutableListOf<OutdatedInfo>()
        // チェックに失敗したプラグインのエラーを収集
        val checkErrors = mutableListOf<PluginCheckError>()

        for ((pluginName, spec) in project.plugins) {
            // unmanagedの場合はスキップ
            if (spec is PluginSpec.Unmanaged) {
                continue
            }

            // 各プラグインの更新情報を取得し、エラーも記録する
            checkOutdated(pluginName).fold(
                { error ->
                    checkErrors.add(PluginCheckError(pluginName.value, error.message))
                },
                { outdatedInfo ->
                    if (outdatedInfo != null) {
                        outdatedInfoList.add(outdatedInfo)
                    }
                }
            )
        }

        return OutdatedCheckResult(outdatedInfoList, checkErrors).right()
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
                // GitHub形式: "owner/repository"
                val parts = repositoryId.split("/")
                if (parts.size != 2) return null
                UrlData.GithubUrlData(owner = parts[0], repository = parts[1])
            }
            "modrinth" -> UrlData.ModrinthUrlData(id = repositoryId)
            "spigotmc" -> UrlData.SpigotMcUrlData(resourceId = repositoryId)
            else -> null
        }
    }
}