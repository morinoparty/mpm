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
import kotlinx.coroutines.sync.Mutex
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.application.model.UpdateResult
import party.morino.mpm.api.application.model.install.BulkInstallResult
import party.morino.mpm.api.application.model.install.InstallResult
import party.morino.mpm.api.application.model.install.PluginInstallInfo
import party.morino.mpm.api.application.model.install.PluginRemovalInfo
import party.morino.mpm.api.application.plugin.IntegrityVerifier
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.application.plugin.PluginUpdateService
import party.morino.mpm.api.application.plugin.model.integrity.IntegrityResult
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
import party.morino.mpm.api.domain.project.lock.LockRepository
import party.morino.mpm.api.domain.project.repository.ProjectRepository
import party.morino.mpm.api.domain.repository.RepositoryManager
import party.morino.mpm.api.model.backup.BackupReason
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import party.morino.mpm.api.shared.error.MpmError
import party.morino.mpm.event.lifecycle.PluginInstallEvent
import party.morino.mpm.event.state.PluginLockEvent
import party.morino.mpm.event.state.PluginUnlockEvent
import party.morino.mpm.event.state.PluginUpdateEvent
import party.morino.mpm.utils.BukkitDispatcher
import party.morino.mpm.utils.DataClassReplacer.replaceTemplate
import java.io.File

/**
 * プラグインの更新を行うApplication Service実装
 *
 * UseCaseのロジックを直接実装
 */
class PluginUpdateServiceImpl :
    PluginUpdateService,
    KoinComponent {
    companion object {
        // ロック中プラグインをスキップした際の共通エラーメッセージ
        private const val LOCKED_ERROR_MESSAGE = "プラグインがロックされています"
    }

    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()
    private val projectRepository: ProjectRepository by inject()
    private val pluginMetadataManager: PluginMetadataManager by inject()
    private val repositoryManager: RepositoryManager by inject()
    private val downloaderRepository: DownloaderRepository by inject()
    private val backupManager: ServerBackupManager by inject()
    private val infoService: PluginInfoService by inject()
    private val plugin: JavaPlugin by inject()

    // ダウンロード済みプラグインのAPIバージョン互換性・依存関係の検証を行う共通ロジック
    // PluginLifecycleServiceImpl.install() と共有し、検証ロジックの重複・乖離を防ぐ
    private val pluginInstallValidator: PluginInstallValidator by inject()

    // ダウンロードしたJARのハッシュ整合性検証を行う
    private val integrityVerifier: IntegrityVerifier by inject()

    // ロックファイル（mpm-lock.yaml）の読み込み（frozenインストールで使用）
    private val lockRepository: LockRepository by inject()

    // 並行更新を防止するためのMutex（スケジューラーとコマンドの競合回避）
    private val updateMutex = Mutex()

    /**
     * 更新可能なすべてのプラグインを更新する
     *
     * UpdatePluginUseCaseImplから移行したロジック
     */
    override suspend fun update(
        force: Boolean,
        progressCallback: ((String) -> Unit)?,
        skipIntegrity: Boolean
    ): Either<MpmError, List<UpdateResult>> {
        // 既に更新処理が実行中の場合はエラーを返す
        if (!updateMutex.tryLock()) {
            return MpmError.PluginError.UpdateInProgress.left()
        }
        try {
            return executeUpdate(force, progressCallback, skipIntegrity)
        } finally {
            updateMutex.unlock()
        }
    }

    /**
     * 更新処理の本体（Mutex保護下で呼び出される）
     */
    private suspend fun executeUpdate(
        force: Boolean,
        progressCallback: ((String) -> Unit)? = null,
        skipIntegrity: Boolean = false
    ): Either<MpmError, List<UpdateResult>> {
        // すべてのプラグインの更新情報を取得
        progressCallback?.invoke("<gray>更新可能なプラグインを確認しています...")
        val checkResult =
            infoService.checkAllOutdated().getOrElse {
                return it.left()
            }

        val outdatedInfoList = checkResult.outdatedPlugins

        // チェックに失敗したプラグインを警告表示し、UpdateResultとしても記録
        val checkFailResults =
            checkResult.errors.map { checkError ->
                plugin.logger.warning("Failed to check update for ${checkError.pluginName}: ${checkError.errorMessage}")
                progressCallback?.invoke(
                    "<gray>[${checkError.pluginName}] <red>チェック失敗: ${checkError.errorMessage}"
                )
                UpdateResult(
                    pluginName = checkError.pluginName,
                    oldVersion = "unknown",
                    newVersion = "unknown",
                    success = false,
                    errorMessage = checkError.errorMessage
                )
            }

        // 更新が必要なプラグインがある場合、バックアップを作成
        val hasUpdates = outdatedInfoList.any { it.needsUpdate }
        if (!hasUpdates && checkFailResults.isEmpty()) {
            progressCallback?.invoke("<green>すべてのプラグインは最新です。")
        }
        if (hasUpdates) {
            progressCallback?.invoke("<gray>バックアップを作成しています...")
            backupManager.createBackup(BackupReason.UPDATE).fold(
                {
                    plugin.logger.warning("バックアップの作成に失敗しました: ${it.message}")
                    progressCallback?.invoke("<yellow>バックアップの作成に失敗しましたが、更新を続行します")
                },
                {
                    plugin.logger.info("バックアップを作成しました: ${it.fileName}")
                    progressCallback?.invoke("<green>バックアップ完了: ${it.fileName}")
                }
            )
        }

        // mpm.jsonを読み込んでSync依存関係を取得
        val mpmConfig = loadMpmConfig()

        // 更新結果のリスト
        val updateResults = mutableListOf<UpdateResult>()

        // 更新が必要なプラグインを処理
        for (outdatedInfo in outdatedInfoList) {
            // 更新が不要な場合はスキップ
            if (!outdatedInfo.needsUpdate) {
                continue
            }

            // sync: プラグインはメインループでは更新しない。
            // 自身のリポジトリの最新ではなく、親のバージョンに追従させる必要があるため、
            // ループ後の updateSyncPlugins（連動更新）でまとめて処理する。
            val specString = mpmConfig?.plugins?.get(outdatedInfo.pluginName)
            if (specString != null && VersionSpecifierParser.isSyncFormat(specString)) {
                continue
            }

            // メタデータを読み込んでロック状態を確認
            val metadata = pluginMetadataManager.loadMetadata(outdatedInfo.pluginName).getOrNull()
            if (metadata == null) {
                progressCallback?.invoke(
                    "<gray>[${outdatedInfo.pluginName}] <red>メタデータの読み込みに失敗しました"
                )
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
                progressCallback?.invoke("<gray>[${outdatedInfo.pluginName}] <yellow>ロック中のためスキップ")
                updateResults.add(
                    UpdateResult(
                        pluginName = outdatedInfo.pluginName,
                        oldVersion = outdatedInfo.currentVersion,
                        newVersion = outdatedInfo.latestVersion,
                        success = false,
                        errorMessage = LOCKED_ERROR_MESSAGE
                    )
                )
                continue
            }

            // PluginUpdateEventを発火して、他のプラグインがキャンセルできるようにする
            // PaperMCではイベントはメインスレッドで発火する必要があるため、BukkitDispatcherを使用
            val updateEvent =
                BukkitDispatcher.callEventSync(
                    plugin,
                    PluginUpdateEvent(
                        installedPlugin = InstalledPlugin(outdatedInfo.pluginName),
                        beforeVersion = VersionSpecifier.Fixed(outdatedInfo.currentVersion),
                        targetVersion = VersionSpecifier.Fixed(outdatedInfo.latestVersion)
                    )
                )

            // イベントがキャンセルされた場合はスキップ
            if (updateEvent.isCancelled) {
                progressCallback?.invoke(
                    "<gray>[${outdatedInfo.pluginName}] <yellow>更新がキャンセルされました"
                )
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

            // イベント通過後にダウンロード開始を通知
            progressCallback?.invoke(
                "<gray>[${outdatedInfo.pluginName}] ${outdatedInfo.currentVersion} → ${outdatedInfo.latestVersion} ダウンロード中..."
            )

            // 最新バージョンでインストール（既存のファイルは上書きされる、forceフラグを伝播）
            val installResult =
                installSinglePlugin(outdatedInfo.pluginName, force, useLatest = true, skipIntegrity = skipIntegrity)

            installResult.fold(
                // インストール失敗時
                { errorMessage ->
                    progressCallback?.invoke(
                        "<gray>[${outdatedInfo.pluginName}] <red>更新失敗: $errorMessage"
                    )
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
                    progressCallback?.invoke(
                        "<gray>[${outdatedInfo.pluginName}] <green>更新完了 ✓"
                    )
                    updateResults.add(
                        UpdateResult(
                            pluginName = outdatedInfo.pluginName,
                            oldVersion = outdatedInfo.currentVersion,
                            newVersion = outdatedInfo.latestVersion,
                            success = true
                        )
                    )
                }
            )
        }

        // Syncプラグインの連動更新（forceフラグを伝播）
        // 一括更新では全ての sync: プラグインを対象に、それぞれの親の（更新後）バージョンへ追従させる。
        // 既に同期済みの子は再取得せずスキップされる。
        mpmConfig?.let { config ->
            val allSyncChildren =
                config.plugins
                    .filterValues { VersionSpecifierParser.isSyncFormat(it) }
                    .keys
            updateSyncPlugins(config, allSyncChildren, updateResults, force, progressCallback, skipIntegrity)
        }

        return (checkFailResults + updateResults).right()
    }

    /**
     * 指定プラグインを更新する
     *
     * 最新バージョンを確認してからインストールする
     */
    override suspend fun update(
        name: PluginName,
        force: Boolean,
        skipIntegrity: Boolean
    ): Either<MpmError, List<UpdateResult>> {
        // 並行更新を防止（jar/metadataファイルの競合回避）
        if (!updateMutex.tryLock()) {
            return MpmError.PluginError.UpdateInProgress.left()
        }
        try {
            val mpmConfig = loadMpmConfig()

            // sync: プラグインを直接更新する場合は、自身のリポジトリの最新ではなく
            // 同期先（親）のバージョンに追従させる（一括更新の連動更新と同じ挙動）。
            // これにより mpm outdated の表示（親に追従）と mpm update <子> の挙動が一致する。
            val specString = mpmConfig?.plugins?.get(name.value)
            if (specString != null && VersionSpecifierParser.isSyncFormat(specString)) {
                val syncResults = mutableListOf<UpdateResult>()
                updateSyncPlugins(
                    mpmConfig = mpmConfig,
                    syncChildren = listOf(name.value),
                    updateResults = syncResults,
                    force = force,
                    skipIntegrity = skipIntegrity
                )
                // 既に親と同期済みで更新が発生しなかった場合は現状維持の成功結果を返す
                if (syncResults.isEmpty()) {
                    val current =
                        pluginMetadataManager.loadMetadata(name.value).fold(
                            { "unknown" },
                            { it.mpmInfo.version.current.raw }
                        )
                    syncResults.add(
                        UpdateResult(
                            pluginName = name.value,
                            oldVersion = current,
                            newVersion = current,
                            success = true,
                            errorMessage = null
                        )
                    )
                }
                return syncResults.right()
            }

            // 更新が必要かチェック
            val outdatedInfo =
                infoService.checkOutdated(name).getOrElse {
                    return it.left()
                }

            // 更新が不要かつforceでない場合は、親の現状維持結果のみを返す（連動更新は行わない）
            if (outdatedInfo == null || (!outdatedInfo.needsUpdate && !force)) {
                return listOf(
                    UpdateResult(
                        pluginName = name.value,
                        oldVersion = outdatedInfo?.currentVersion ?: "unknown",
                        newVersion = outdatedInfo?.latestVersion ?: "unknown",
                        success = true,
                        errorMessage = null
                    )
                ).right()
            }

            // ロック状態を確認
            val metadata =
                pluginMetadataManager.loadMetadata(name.value).getOrElse {
                    return MpmError.PluginError.MetadataNotFound(name.value).left()
                }
            if (metadata.mpmInfo.settings.lock == true && !force) {
                return MpmError.PluginError.Locked(name.value).left()
            }

            // PluginUpdateEventを発火して、キャンセル可能にする
            val updateEvent =
                BukkitDispatcher.callEventSync(
                    plugin,
                    PluginUpdateEvent(
                        installedPlugin = InstalledPlugin(name.value),
                        beforeVersion = VersionSpecifier.Fixed(outdatedInfo.currentVersion),
                        targetVersion = VersionSpecifier.Fixed(outdatedInfo.latestVersion)
                    )
                )
            if (updateEvent.isCancelled) {
                return MpmError.PluginError.OperationCancelled(name.value, "update").left()
            }

            // 一括更新と同様に更新前バックアップを作成する（Codex P2-3）
            backupManager.createBackup(BackupReason.UPDATE).fold(
                { error -> plugin.logger.warning("[update] バックアップ作成失敗: ${error.message} - 更新を続行") },
                { info -> plugin.logger.info("[update] バックアップ作成完了: ${info.fileName}") }
            )

            // 更新結果（先頭が親、以降が連動更新した子）
            val updateResults = mutableListOf<UpdateResult>()

            // 最新バージョンをtargetVersionとして渡してインストール
            installSinglePlugin(name.value, force, useLatest = true, skipIntegrity = skipIntegrity).fold(
                { error -> return MpmError.PluginError.UpdateFailed(name.value, error).left() },
                {
                    updateResults.add(
                        UpdateResult(
                            pluginName = name.value,
                            oldVersion = outdatedInfo.currentVersion,
                            newVersion = outdatedInfo.latestVersion,
                            success = true,
                            errorMessage = null
                        )
                    )
                }
            )

            // 連動更新: この親に同期している sync: プラグイン（子）を親の新バージョンに追従させる
            mpmConfig?.let { config ->
                updateSyncPlugins(
                    mpmConfig = config,
                    syncChildren = config.getPluginsSyncingTo(name.value),
                    updateResults = updateResults,
                    force = force,
                    skipIntegrity = skipIntegrity
                )
            }

            return updateResults.right()
        } finally {
            updateMutex.unlock()
        }
    }

    /**
     * mpm.jsonに記載されているすべてのプラグインをインストールする
     *
     * BulkInstallUseCaseImplから移行したロジック
     */
    override suspend fun installAll(
        force: Boolean,
        skipIntegrity: Boolean,
        frozen: Boolean
    ): Either<MpmError, BulkInstallResult> {
        // 並行更新を防止（jar/metadataファイルの競合回避）
        if (!updateMutex.tryLock()) {
            return MpmError.PluginError.UpdateInProgress.left()
        }
        try {
            // frozen指定時はロックファイルどおりの正確なバージョンをインストールする
            return if (frozen) {
                executeFrozenInstall(force, skipIntegrity)
            } else {
                executeInstallAll(force, skipIntegrity)
            }
        } finally {
            updateMutex.unlock()
        }
    }

    /**
     * mpm-lock.yaml に記録された正確なバージョンをインストールする（再現インストール / npm ci 相当）
     *
     * mpm.jsonのlatest/tag指定は無視し、ロックファイルのバージョンをそのまま導入する。
     * ロックファイルが存在しない場合、および管理下プラグインがロックに含まれていない場合（ドリフト）は
     * エラーとして扱う。
     */
    private suspend fun executeFrozenInstall(
        force: Boolean,
        skipIntegrity: Boolean
    ): Either<MpmError, BulkInstallResult> {
        // ロックファイルを読み込む。未存在と破損を区別する
        // （破損時に 'mpm install' を促すと再生成で上書きされ再現性が失われるため、明確に区別する）
        val lock =
            lockRepository.find()
                ?: return if (lockRepository.exists()) {
                    MpmError.ProjectError
                        .ConfigParseError(
                            "mpm-lock.yaml が破損しています。手動で確認・修正してください（再現インストールのため自動再生成しません）。"
                        ).left()
                } else {
                    MpmError.ProjectError
                        .ConfigParseError(
                            "mpm-lock.yaml が見つかりません。先に 'mpm install' を実行してロックファイルを生成してください。"
                        ).left()
                }

        // プロジェクト（管理下プラグイン）を取得
        val project = projectRepository.findOrError().getOrElse { return it.left() }
        val mpmConfig = project.toDto()

        // 依存順にインストールするためトポロジカルソートする
        val sortedPlugins = mpmConfig.topologicalSortPlugins()

        val installed = mutableListOf<PluginInstallInfo>()
        val removed = mutableListOf<PluginRemovalInfo>()
        val failed = mutableMapOf<String, String>()

        for (pluginName in sortedPlugins) {
            val expectedVersion = mpmConfig.plugins[pluginName] ?: continue
            // unmanagedはロック対象外なのでスキップ
            if (expectedVersion == "unmanaged") continue

            // ロックにエントリが無い管理下プラグインはドリフトとして失敗扱いにする
            val lockEntry = lock.plugins[pluginName]
            if (lockEntry == null) {
                failed[pluginName] = "ロックファイルにエントリがありません（mpm install で再生成してください）"
                continue
            }

            // ロックに記録された正確なバージョンとsha256でインストールする
            // （sha256を渡すことで、ダウンロードしたバイト列がロックと一致することを保証する）
            installPluginWithVersion(
                pluginName = pluginName,
                expectedVersion = lockEntry.version.raw,
                force = force,
                skipIntegrity = skipIntegrity,
                expectedSha256 = lockEntry.download.sha256
            ).fold(
                { failed[pluginName] = it },
                { result ->
                    installed.add(result.installed)
                    result.removed?.let { removed.add(it) }
                }
            )
        }

        return BulkInstallResult(installed = installed, removed = removed, failed = failed).right()
    }

    /**
     * 一括インストール処理の本体（Mutex保護下で呼び出される）
     */
    private suspend fun executeInstallAll(
        force: Boolean,
        skipIntegrity: Boolean = false
    ): Either<MpmError, BulkInstallResult> {
        // ProjectRepositoryを通じてプロジェクトを取得（パースエラーも区別する）
        val mpmConfig =
            projectRepository
                .findOrError()
                .map { it.toDto() }
                .getOrElse { return it.left() }

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
        // ロック中のため更新をスキップしたプラグイン（executeUpdateと同様にlockを尊重する）
        val lockedSkipped = mutableListOf<String>()
        for (pluginName in sortedPlugins) {
            val expectedVersion = mpmConfig.plugins[pluginName] ?: continue
            if (expectedVersion == "unmanaged") continue

            val resolvedVersion = resolveExpectedVersion(pluginName, expectedVersion, resolvedVersions)
            val metadataResult = pluginMetadataManager.loadMetadata(pluginName)

            // インストールが必要かを判定
            // latestとtag:は動的にバージョンが決まるため、installPluginWithVersionに委譲する
            val isDynamic = expectedVersion == "latest" || VersionSpecifierParser.isTagFormat(expectedVersion)
            val isLocked = metadataResult.fold({ false }, { it.mpmInfo.settings.lock == true })
            val needsUpdate =
                metadataResult.fold(
                    { true }, // メタデータなし → インストール必要
                    { metadata ->
                        // latest/tag: は動的なため常に installPluginWithVersion に委譲する（#283）
                        isDynamic || metadata.mpmInfo.version.current.raw != resolvedVersion
                    }
                )

            // ロック中のプラグインは上書きせずスキップする（mpm.jsonのバージョン変更を無視）
            if (isLocked && needsUpdate) {
                lockedSkipped.add(pluginName)
            }
            val shouldInstall = needsUpdate && !isLocked

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
        // ロックによりスキップしたプラグインもfailedとして報告する（executeUpdateのUpdateResultと同様の扱い）
        lockedSkipped.forEach { failed[it] = LOCKED_ERROR_MESSAGE }

        // 各プラグインをトポロジカルソート順にインストール
        for (pluginName in sortedPlugins) {
            val versionString = mpmConfig.plugins[pluginName] ?: continue

            if (pluginName !in pluginsToInstall) {
                // ロックによりスキップした場合は検出ループで実インストール済みバージョンを記録済みのため、
                // mpm.json上の未インストールターゲットバージョンで上書きしない（Sync解決の破損を防ぐ）
                if (pluginName in lockedSkipped) {
                    continue
                }
                // インストール不要でもバージョンを記録（後続のSyncプラグインのため）
                if (versionString != "unmanaged" && !VersionSpecifierParser.isSyncFormat(versionString)) {
                    resolvedVersions[pluginName] = resolveExpectedVersion(pluginName, versionString, resolvedVersions)
                }
                continue
            }

            val versionToInstall = resolveExpectedVersion(pluginName, versionString, resolvedVersions)
            installPluginWithVersion(pluginName, versionToInstall, force, skipIntegrity).fold(
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
        // PaperMCではイベントはメインスレッドで発火する必要があるため、BukkitDispatcherを使用
        val lockEvent =
            BukkitDispatcher.callEventSync(
                plugin,
                PluginLockEvent(
                    installedPlugin = InstalledPlugin(name.value),
                    currentVersion = metadata.mpmInfo.version.current.raw
                )
            )

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
        // PaperMCではイベントはメインスレッドで発火する必要があるため、BukkitDispatcherを使用
        val unlockEvent =
            BukkitDispatcher.callEventSync(
                plugin,
                PluginUnlockEvent(
                    installedPlugin = InstalledPlugin(name.value),
                    currentVersion = metadata.mpmInfo.version.current.raw
                )
            )

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
     * sync: プラグイン（子）を、その同期先（親）の現在バージョンに追従して連動更新する
     *
     * 各子について親のインストール済みバージョンを解決し、既に一致していれば何もしない。
     * 一致していなければ [installPluginWithVersion] で親のバージョンを子のリポジトリから取得して置換する。
     * 1件の失敗は該当プラグインの失敗結果として記録し、残りの処理は継続する。
     *
     * @param mpmConfig mpm.json の設定（sync ターゲット解決に使用）
     * @param syncChildren 連動更新の対象とする sync: プラグイン名の集合
     */
    private suspend fun updateSyncPlugins(
        mpmConfig: MpmConfig,
        syncChildren: Collection<String>,
        updateResults: MutableList<UpdateResult>,
        force: Boolean = false,
        progressCallback: ((String) -> Unit)? = null,
        skipIntegrity: Boolean = false
    ) {
        if (syncChildren.isEmpty()) {
            return
        }
        progressCallback?.invoke("<gray>連動更新を確認しています...")

        for (childName in syncChildren) {
            // 子の同期先（親プラグイン名）を mpm.json の sync: 指定から特定する
            val syncTarget =
                mpmConfig.plugins[childName]?.let { VersionSpecifierParser.extractSyncTarget(it) }
            // 親の（更新後の）インストール済みバージョンを解決する
            val targetVersion =
                syncTarget?.let { parent ->
                    pluginMetadataManager.loadMetadata(parent).fold({ null }, { it.mpmInfo.version.current.raw })
                }

            // 子の現在バージョンとロック状態を取得
            val childMetadata = pluginMetadataManager.loadMetadata(childName)
            val currentVersion = childMetadata.fold({ "unknown" }, { it.mpmInfo.version.current.raw })
            val isLocked = childMetadata.fold({ false }, { it.mpmInfo.settings.lock == true })

            // ロックされている場合はスキップ（現状維持）
            if (isLocked) {
                progressCallback?.invoke("<gray>[$childName] <yellow>ロック中のためスキップ")
                updateResults.add(
                    UpdateResult(
                        pluginName = childName,
                        oldVersion = currentVersion,
                        newVersion = currentVersion,
                        success = false,
                        errorMessage = LOCKED_ERROR_MESSAGE
                    )
                )
                continue
            }

            // 親のバージョンを解決できない場合はスキップ（親のメタデータ欠落など）
            if (targetVersion == null) {
                progressCallback?.invoke("<gray>[$childName] <yellow>同期先のバージョンを解決できませんでした")
                updateResults.add(
                    UpdateResult(
                        pluginName = childName,
                        oldVersion = currentVersion,
                        newVersion = currentVersion,
                        success = false,
                        errorMessage = "同期先 '${syncTarget ?: "?"}' のバージョンを解決できませんでした"
                    )
                )
                continue
            }

            // 既に親のバージョンに同期済みなら再取得しない
            if (targetVersion == currentVersion) {
                continue
            }

            // 親のバージョンを子のリポジトリから取得して置換する
            progressCallback?.invoke(
                "<gray>[$childName] $currentVersion → $targetVersion 連動更新をダウンロード中..."
            )
            installPluginWithVersion(childName, targetVersion, force, skipIntegrity).fold(
                // インストール失敗時
                { errorMessage ->
                    progressCallback?.invoke("<gray>[$childName] <red>連動更新失敗: $errorMessage")
                    updateResults.add(
                        UpdateResult(
                            pluginName = childName,
                            oldVersion = currentVersion,
                            newVersion = targetVersion,
                            success = false,
                            errorMessage = "連動更新に失敗: $errorMessage"
                        )
                    )
                },
                // インストール成功時
                { result ->
                    progressCallback?.invoke("<gray>[$childName] <green>連動更新完了 ✓")
                    updateResults.add(
                        UpdateResult(
                            pluginName = childName,
                            oldVersion = currentVersion,
                            newVersion = result.installed.currentVersion,
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
    private suspend fun installSinglePlugin(
        pluginName: String,
        force: Boolean = false,
        useLatest: Boolean = false,
        skipIntegrity: Boolean = false
    ): Either<String, InstallResult> {
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

        // mpm.jsonからtag指定を取得（tag:指定の場合はチャンネル別の最新を取得する）
        val mpmConfig = loadMpmConfig()
        val versionString = mpmConfig?.plugins?.get(pluginName)
        val tagChannelForPlugin = versionString?.let { VersionSpecifierParser.extractTag(it) }

        // チャンネル設定(versionMatcher/useUpstreamLabel)を取得するためリポファイルを参照。
        // metadata.repositoryに対応する RepositoryConfig を厳密マッチで特定する（見つからなければ先頭）
        val repositoryFile = repositoryManager.getRepositoryFile(pluginName)
        val matchingRepositoryConfig =
            repositoryFile
                ?.repositories
                ?.firstOrNull {
                    it.type.equals(repositoryInfo.type.name, ignoreCase = true) &&
                        it.repositoryId == repositoryInfo.id
                }
                ?: repositoryFile?.repositories?.firstOrNull()

        // 最新バージョンを取得（tag:指定の場合は該当チャンネルの最新を取得）
        val latestVersionData =
            try {
                if (tagChannelForPlugin != null) {
                    ChannelVersionResolver.resolveTag(
                        downloaderRepository,
                        urlData,
                        matchingRepositoryConfig,
                        tagChannelForPlugin
                    ) ?: return "tag '$tagChannelForPlugin' に該当するバージョンが見つかりません: $pluginName".left()
                } else {
                    ChannelVersionResolver.resolveLatest(
                        downloaderRepository,
                        urlData,
                        matchingRepositoryConfig
                    )
                }
            } catch (e: Exception) {
                return "最新バージョン情報の取得に失敗しました: ${e.message}".left()
            }

        // useLatestの場合は最新バージョンでDL、そうでなければメタデータの現在バージョンでDL
        val versionData =
            if (useLatest) {
                latestVersionData
            } else {
                VersionData(mpmInfoDto.download.downloadId, mpmInfoDto.version.current.raw)
            }
        val action = if (useLatest) "update" else "install"

        // メタデータを更新（最新バージョン情報を反映）
        val updatedMetadataWithLatest =
            pluginMetadataManager
                .updateMetadata(pluginName, versionData, latestVersionData, action)
                .getOrElse { return it.left() }

        // PluginInstallEventを発火
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

        // ダウンロードしたtempファイルの整合性を検証する（ステージング前に実施）
        // mpmInfoDtoは更新前のメタデータなので、同一バージョンのインストール（再取得）時のみ
        // 保存済みsha256を照合に使用する。バージョンアップ時は旧ハッシュで誤検知しないようnullを渡す。
        val scopedStoredSha256 =
            if (versionData.version == mpmInfoDto.version.current.raw) mpmInfoDto.download.sha256 else null
        val verifiedSha256 =
            verifyIntegrityOrAbort(
                downloadedFile = downloadedFile,
                urlData = urlData,
                versionName = versionData.version,
                storedSha256 = scopedStoredSha256,
                fileNamePattern = mpmInfoDto.fileNamePattern,
                skipIntegrity = skipIntegrity,
                pluginName = pluginName
            ).getOrElse { return it.left() }

        // tempファイルに対してAPIバージョンと依存関係の事前チェックを行う
        // チェックに失敗した場合はtempファイルを削除して早期リターン
        validateDownloadedPlugin(downloadedFile, pluginName, force).onLeft { error ->
            downloadedFile.delete()
            return error.left()
        }

        // 更新後のメタデータからバージョン情報を取得してファイル名を生成
        val template = mpmInfoDto.fileNameTemplate ?: "<pluginInfo.name>-<mpmInfo.version.current.normalized>.jar"
        val updatedVersion = updatedMetadataWithLatest.mpmInfo.version.current.normalized
        val newFileName = generateFileName(template, pluginInfoDto.name, updatedVersion)

        // staged copy: 一時ファイル経由で安全にファイルを置換する
        // 同名ファイルの上書き中にクラッシュしても既存JARが壊れないようにする
        val pluginsDir = pluginDirectory.getPluginsDirectory()
        val targetFile = File(pluginsDir, newFileName)
        val stagedFile = File(pluginsDir, "$newFileName.tmp")
        try {
            downloadedFile.copyTo(stagedFile, overwrite = true)
            downloadedFile.delete()
            // staged fileを最終位置にリネーム（同一ファイルシステム上ではアトミック）
            if (!stagedFile.renameTo(targetFile)) {
                // renameToが失敗した場合はcopy+deleteにフォールバック
                stagedFile.copyTo(targetFile, overwrite = true)
                stagedFile.delete()
            }
        } catch (e: Exception) {
            stagedFile.delete()
            return "プラグインファイルの移動に失敗しました: ${e.message}".left()
        }

        // 新しいファイルの配置が成功してから古いファイルを削除する
        val oldFileName = mpmInfoDto.download.fileName
        var removedInfo: PluginRemovalInfo? = null
        if (oldFileName != null && oldFileName != newFileName) {
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

        // ファイル名と検証済みsha256をmetadataに記録して保存
        val updatedMetadata =
            updatedMetadataWithLatest.copy(
                mpmInfo =
                    updatedMetadataWithLatest.mpmInfo.copy(
                        download =
                            updatedMetadataWithLatest.mpmInfo.download.copy(
                                fileName = newFileName,
                                sha256 = verifiedSha256
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
        expectedVersion: String,
        force: Boolean = false,
        skipIntegrity: Boolean = false,
        expectedSha256: String? = null
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

        // 最新バージョンを取得（tag:指定の場合は該当チャンネルの最新を取得）
        // チャンネル設定(versionMatcher/useUpstreamLabel)を尊重する
        val tagChannel = VersionSpecifierParser.extractTag(expectedVersion)
        val latestVersionData =
            try {
                if (tagChannel != null) {
                    ChannelVersionResolver.resolveTag(
                        downloaderRepository,
                        urlData,
                        firstRepository,
                        tagChannel
                    ) ?: return "tag '$tagChannel' に該当するバージョンが見つかりません: $pluginName".left()
                } else {
                    ChannelVersionResolver.resolveLatest(
                        downloaderRepository,
                        urlData,
                        firstRepository
                    )
                }
            } catch (e: Exception) {
                return "バージョン情報の取得に失敗しました: ${e.message}".left()
            }

        // 指定バージョンを取得
        val versionData =
            if (expectedVersion == "latest" || tagChannel != null) {
                // latestとtag:はどちらも最新バージョンをそのまま使用
                latestVersionData
            } else {
                try {
                    downloaderRepository.getVersionByName(urlData, expectedVersion)
                } catch (e: Exception) {
                    return "指定されたバージョン '$expectedVersion' の取得に失敗しました: ${e.message}".left()
                }
            }

        // 更新前の保存済みバージョン/ハッシュを退避する
        // （後続のupdateMetadataでmetadataのcurrentが新バージョンに書き換わるため、事前に捕捉する）
        val previousMetadata = pluginMetadataManager.loadMetadata(pluginName).getOrNull()
        val previousStoredVersion =
            previousMetadata
                ?.mpmInfo
                ?.version
                ?.current
                ?.raw
        val previousStoredSha256 = previousMetadata?.mpmInfo?.download?.sha256

        // メタデータが存在するか確認し、更新または作成
        // 新規作成時はチャンネル固有のversionModifierを尊重するため、解決チャンネルを渡す
        val resolvedChannel = tagChannel ?: "latest"
        val metadata =
            pluginMetadataManager.loadMetadata(pluginName).fold(
                // メタデータが存在しない場合は新規作成
                {
                    pluginMetadataManager
                        .createMetadata(pluginName, firstRepository, versionData, "install", resolvedChannel)
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

        // frozenインストール時は、ロックファイルに記録されたsha256を最優先で照合する。
        // リポジトリ側でアーティファクトが同じバージョン名のまま差し替えられていても検出でき、
        // バイト単位の再現性（npm ci相当）を保証する。--skip-integrityでも省略しない（frozenの本質のため）。
        if (expectedSha256 != null) {
            val actualSha256 = integrityVerifier.computeSha256(downloadedFile)
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                downloadedFile.delete()
                return (
                    "ロックファイルのハッシュと一致しません (sha256): " +
                        "expected=$expectedSha256, actual=$actualSha256。" +
                        "アーティファクトが差し替えられた可能性があります。"
                ).left()
            }
        }

        // ダウンロードしたtempファイルの整合性を検証する（ステージング前に実施）
        // 保存済みsha256は、ダウンロードするバージョンが更新前のバージョンと一致する（再取得）場合のみ
        // 照合に使用する。バージョンアップ時は旧ハッシュで誤検知しないようnullを渡す。
        val scopedStoredSha256 =
            if (versionData.version == previousStoredVersion) previousStoredSha256 else null
        val verifiedSha256 =
            verifyIntegrityOrAbort(
                downloadedFile = downloadedFile,
                urlData = urlData,
                versionName = versionData.version,
                storedSha256 = scopedStoredSha256,
                fileNamePattern = firstRepository.fileNamePattern,
                skipIntegrity = skipIntegrity,
                pluginName = pluginName
            ).getOrElse { return it.left() }

        // tempファイルに対してAPIバージョンと依存関係の事前チェックを行う
        // チェックに失敗した場合はtempファイルを削除して早期リターン
        validateDownloadedPlugin(downloadedFile, pluginName, force).onLeft { error ->
            downloadedFile.delete()
            return error.left()
        }

        // ファイル名を生成
        val template = firstRepository.fileNameTemplate ?: "<pluginInfo.name>-<mpmInfo.version.current.normalized>.jar"
        val newFileName = generateFileName(template, pluginName, metadata.mpmInfo.version.current.normalized)

        // staged copy: 一時ファイル経由で安全にファイルを置換する
        val pluginsDir = pluginDirectory.getPluginsDirectory()
        val targetFile = File(pluginsDir, newFileName)
        val stagedFile = File(pluginsDir, "$newFileName.tmp")
        try {
            downloadedFile.copyTo(stagedFile, overwrite = true)
            downloadedFile.delete()
            if (!stagedFile.renameTo(targetFile)) {
                stagedFile.copyTo(targetFile, overwrite = true)
                stagedFile.delete()
            }
        } catch (e: Exception) {
            stagedFile.delete()
            return "プラグインファイルの移動に失敗しました: ${e.message}".left()
        }

        // 新しいファイルの配置が成功してから古いファイルを削除する
        val oldFileName = metadata.mpmInfo.download.fileName
        var removedInfo: PluginRemovalInfo? = null
        if (oldFileName != null && oldFileName != newFileName) {
            val oldFile = File(pluginsDir, oldFileName)
            if (oldFile.exists()) {
                oldFile.delete()
                removedInfo =
                    PluginRemovalInfo(
                        name = pluginName,
                        version = metadata.mpmInfo.version.current.normalized
                    )
            }
        }

        // ファイル名と検証済みsha256をmetadataに記録して保存
        val updatedMetadata =
            metadata.copy(
                mpmInfo =
                    metadata.mpmInfo.copy(
                        download =
                            metadata.mpmInfo.download.copy(
                                fileName = newFileName,
                                sha256 = verifiedSha256
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
     * ダウンロードしたファイルの整合性を検証し、不一致であれば更新を中断する
     *
     * 検証にはリポジトリ提供ハッシュ、または（同一バージョンの場合のみ）保存済みsha256を用いる。
     * 不一致かつ [skipIntegrity] が false の場合はtempファイルを削除してエラーメッセージを返す。
     * [skipIntegrity] が true の場合は警告ログを出力して続行するが、検証をパスしていないため
     * sha256は保存しない（nullを返す）。これにより後続の `mpm verify` が誤ってOKと報告するのを防ぐ。
     *
     * @param storedSha256 照合対象の保存済みsha256（ダウンロードするバージョンと一致する場合のみ渡す）
     * @param fileNamePattern ダウンロード時と同じファイル選択に使用するパターン
     * @return 成功時はメタデータに保存すべきsha256（skip時はnull）、不一致で中断する場合はエラーメッセージ
     */
    private suspend fun verifyIntegrityOrAbort(
        downloadedFile: File,
        urlData: UrlData,
        versionName: String,
        storedSha256: String?,
        fileNamePattern: String?,
        skipIntegrity: Boolean,
        pluginName: String
    ): Either<String, String?> {
        val result = integrityVerifier.verify(downloadedFile, urlData, versionName, storedSha256, fileNamePattern)
        return when (result) {
            is IntegrityResult.Mismatch -> {
                if (skipIntegrity) {
                    plugin.logger.warning(
                        "Integrity check mismatch for '$pluginName' (${result.algorithm}): " +
                            "expected=${result.expected}, actual=${result.actual}. Skipped by --skip-integrity."
                    )
                    // 検証をパスしていないため、信頼済みハッシュとしては保存しない
                    null.right()
                } else {
                    downloadedFile.delete()
                    (
                        "整合性検証に失敗しました (${result.algorithm}): " +
                            "expected=${result.expected}, actual=${result.actual}。" +
                            "ダウンロードが破損または改竄されている可能性があります。--skip-integrityで上書きできます。"
                    ).left()
                }
            }
            is IntegrityResult.Verified -> result.sha256.right()
            is IntegrityResult.NoReference -> result.sha256.right()
        }
    }

    /**
     * mpm.jsonを読み込む（ProjectRepository経由）
     */
    private suspend fun loadMpmConfig(): MpmConfig? = projectRepository.find()?.toDto()

    /**
     * バージョン指定文字列を実際のバージョンに解決する
     *
     * tag:指定の場合はlatestと同様にメタデータから現在バージョンを返す
     * （実際のタグ解決はinstallPluginWithVersionで行う）
     */
    private fun resolveExpectedVersion(
        pluginName: String,
        expected: String,
        resolved: Map<String, String>
    ): String {
        val syncTarget = VersionSpecifierParser.extractSyncTarget(expected)
        return when {
            syncTarget != null -> resolved[syncTarget] ?: expected
            // latestとtag:はどちらも動的解決が必要なため、メタデータの現在バージョンを返す
            expected == "latest" || VersionSpecifierParser.isTagFormat(expected) ->
                pluginMetadataManager.loadMetadata(pluginName).fold(
                    { expected },
                    { it.mpmInfo.version.current.raw }
                )
            else -> expected
        }
    }

    /**
     * ダウンロード済みのtempファイルに対してAPIバージョンと依存関係の事前検証を行う
     *
     * 実際の検証ロジックは [PluginInstallValidator] に集約されており、
     * PluginLifecycleServiceImpl.install() と共通のロジックを利用する。
     * ここでは検証結果をこのサービス独自のエラーメッセージ表現に変換するのみを行う。
     *
     * @param downloadedFile ダウンロード済みのtempファイル
     * @param pluginName プラグイン名（ログ出力用）
     * @param force trueの場合、非互換でも警告のみで続行する
     * @return 検証成功時はUnit、失敗時はエラーメッセージ
     */
    private suspend fun validateDownloadedPlugin(
        downloadedFile: File,
        pluginName: String,
        force: Boolean
    ): Either<String, Unit> =
        when (val result = pluginInstallValidator.validate(downloadedFile, pluginName, force)) {
            is PluginInstallValidationResult.Valid -> Unit.right()
            is PluginInstallValidationResult.ApiVersionIncompatible ->
                (
                    "[API_VERSION_INCOMPATIBLE] api-version非互換: " +
                        "プラグインは${result.pluginApiVersion}を要求していますが、" +
                        "サーバーは${result.serverApiVersion}です"
                ).left()
            is PluginInstallValidationResult.MissingDependencies ->
                "必須依存プラグインが不足しています: ${result.missingDependencies.joinToString(", ")}".left()
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
            "hangar" -> {
                // Hangar形式: "owner/project"（ownerを省略したslug単体も許容する）
                val parts = repositoryId.split("/")
                when (parts.size) {
                    2 -> UrlData.HangarUrlData(owner = parts[0], projectName = parts[1])
                    1 -> UrlData.HangarUrlData(owner = "", projectName = parts[0])
                    else -> null
                }
            }
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