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
import party.morino.mpm.api.application.lock.LockService
import party.morino.mpm.api.application.model.UpdateResult
import party.morino.mpm.api.application.model.install.BulkInstallResult
import party.morino.mpm.api.application.model.install.InstallResult
import party.morino.mpm.api.application.model.install.PluginInstallInfo
import party.morino.mpm.api.application.model.install.PluginRemovalInfo
import party.morino.mpm.api.application.plugin.IntegrityVerifier
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.application.plugin.PluginUpdateService
import party.morino.mpm.api.application.plugin.model.integrity.IntegrityResult
import party.morino.mpm.api.application.project.ProjectService
import party.morino.mpm.api.domain.backup.ServerBackupManager
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.downloader.DownloaderRepository
import party.morino.mpm.api.domain.downloader.model.UrlData
import party.morino.mpm.api.domain.downloader.model.VersionData
import party.morino.mpm.api.domain.plugin.dto.ManagedPluginDto
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.domain.plugin.model.PluginSpec
import party.morino.mpm.api.domain.plugin.model.VersionDetail
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.domain.plugin.model.VersionSpecifierParser
import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import party.morino.mpm.api.domain.project.dto.MpmConfig
import party.morino.mpm.api.domain.project.dto.getSyncDescendants
import party.morino.mpm.api.domain.project.dto.topologicalSortPlugins
import party.morino.mpm.api.domain.project.dto.validateSyncDependencies
import party.morino.mpm.api.domain.project.lock.LockRepository
import party.morino.mpm.api.domain.project.repository.ProjectRepository
import party.morino.mpm.api.domain.repository.RepositoryManager
import party.morino.mpm.api.model.backup.BackupReason
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import party.morino.mpm.api.shared.error.MpmError
import party.morino.mpm.application.plugin.install.InstallCandidate
import party.morino.mpm.application.plugin.install.planInstallTargets
import party.morino.mpm.application.plugin.metadata.restoreQuarantinedMetadataOrWarn
import party.morino.mpm.event.lifecycle.PluginInstallEvent
import party.morino.mpm.event.state.PluginLockEvent
import party.morino.mpm.event.state.PluginUnlockEvent
import party.morino.mpm.event.state.PluginUpdateEvent
import party.morino.mpm.infrastructure.downloader.PluginDownloadException
import party.morino.mpm.utils.BukkitDispatcher
import party.morino.mpm.utils.DataClassReplacer.replaceTemplate
import party.morino.mpm.utils.regenerateQuietly
import party.morino.mpm.utils.replaceJarAtomically
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

        // メタデータを読めずロック状態を判定できないためスキップした際のエラーメッセージ
        private const val METADATA_UNREADABLE_ERROR_MESSAGE =
            "メタデータを読み込めないためロック状態を確認できず、連動更新をスキップしました"

        // バージョン切り替え時に履歴へ記録するアクション名
        private const val ACTION_SWITCH = "switch"

        // 切り戻し時に履歴へ記録するアクション名
        private const val ACTION_ROLLBACK = "rollback"
    }

    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()
    private val projectRepository: ProjectRepository by inject()
    private val pluginMetadataManager: PluginMetadataManager by inject()
    private val repositoryManager: RepositoryManager by inject()
    private val downloaderRepository: DownloaderRepository by inject()
    private val backupManager: ServerBackupManager by inject()
    private val infoService: PluginInfoService by inject()

    // mpm.jsonの保存（バージョン切り替え時のFixed書き換え）に使用する
    // ProjectRepository.save()はエラーを返さないため、Eitherで失敗を扱えるProjectServiceを使う
    private val projectService: ProjectService by inject()
    private val plugin: JavaPlugin by inject()

    // ダウンロード済みプラグインのAPIバージョン互換性・依存関係の検証を行う共通ロジック
    // PluginLifecycleServiceImpl.install() と共有し、検証ロジックの重複・乖離を防ぐ
    private val pluginInstallValidator: PluginInstallValidator by inject()

    // ダウンロードしたJARのハッシュ整合性検証を行う
    private val integrityVerifier: IntegrityVerifier by inject()

    // ロックファイル（mpm-lock.yaml）の読み込み（frozenインストールで使用）
    private val lockRepository: LockRepository by inject()

    // バージョン切り替え後にロックファイルを実インストール状態へ追従させる
    // コマンド経路とHTTP経路の両方から再生成されるよう、サービス層で呼び出す
    private val lockService: LockService by inject()

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
                        errorMessage = LOCKED_ERROR_MESSAGE,
                        // ロックは異常ではなく意図的な据え置きなので、sync連動側と同じくスキップとして扱う。
                        // ここを失敗のままにすると、同じ理由なのに非syncは赤、sync子は黄と表示が割れる。
                        skipped = true
                    )
                )
                continue
            }

            // メタデータを置き換えられるかを、キャンセル可能なイベントを発火するより前に検査する（副作用なし）。
            // 未来のスキーマ版数で書かれたファイルは読み込みに成功してしまうため、ここを通さないと
            // 「中止が確定している更新」を他プラグインへ通知してしまう（Webhookの外部通知は取り消せない）。
            // 単体更新 update(name) と同じく「破壊的操作・イベント発火の前に中止する」方へ揃える
            // （installSinglePlugin 側の同じ判定は、他の呼び出し経路のための多重防御として残す）。
            val preflightError =
                pluginMetadataManager
                    .ensureMetadataReplaceable(outdatedInfo.pluginName)
                    .fold({ it }, { null })
            if (preflightError != null) {
                progressCallback?.invoke("<gray>[${outdatedInfo.pluginName}] <red>$preflightError")
                updateResults.add(
                    UpdateResult(
                        pluginName = outdatedInfo.pluginName,
                        oldVersion = outdatedInfo.currentVersion,
                        newVersion = outdatedInfo.latestVersion,
                        success = false,
                        errorMessage = preflightError
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
                { error ->
                    progressCallback?.invoke(
                        "<gray>[${outdatedInfo.pluginName}] <red>更新失敗: ${error.message}"
                    )
                    updateResults.add(
                        UpdateResult(
                            pluginName = outdatedInfo.pluginName,
                            oldVersion = outdatedInfo.currentVersion,
                            newVersion = outdatedInfo.latestVersion,
                            success = false,
                            errorMessage = error.message
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
            // 多段sync（親 <- 子 <- 孫）でも親から順に追従できるよう、トポロジカル順に並べ替える
            val allSyncChildren =
                config.topologicalSortPlugins().filter { name ->
                    config.plugins[name]?.let { VersionSpecifierParser.isSyncFormat(it) } == true
                }
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
        skipIntegrity: Boolean,
        skipBackup: Boolean
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
                    // 自身に続けて、自身に同期している子孫も追従させる（多段syncの伝播）
                    syncChildren = listOf(name.value) + mpmConfig.getSyncDescendants(name.value),
                    updateResults = syncResults,
                    force = force,
                    skipIntegrity = skipIntegrity
                )
                // 既に親と同期済みで更新が発生しなかった場合は現状維持の成功結果を返す
                if (syncResults.none { it.pluginName == name.value }) {
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

            // メタデータを置き換えられるかを、イベント発火とバックアップ作成より前に検査する（副作用なし）。
            // 未来のスキーマ版数で書かれたファイルは読み込みに成功するため、ここを通さないと
            // 中止が確定している操作のために plugins/ ディレクトリ全体のZIPを作り、
            // 起きるはずのない更新を他プラグインへ通知してしまう。
            // add / uninstall / install と同じく「破壊的操作の前に中止する」方に揃える
            // （installSinglePlugin 側の同じ判定は、他の呼び出し経路のための多重防御として残す）。
            pluginMetadataManager.ensureMetadataReplaceable(name.value).onLeft {
                return MpmError.PluginError.UpdateFailed(name.value, it).left()
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
            // 呼び出し側で既にバックアップ済みの場合（スケジューラの一括処理など）はskipBackupで抑制する
            if (!skipBackup) {
                backupManager.createBackup(BackupReason.UPDATE).fold(
                    { error -> plugin.logger.warning("[update] バックアップ作成失敗: ${error.message} - 更新を続行") },
                    { info -> plugin.logger.info("[update] バックアップ作成完了: ${info.fileName}") }
                )
            }

            // 更新結果（先頭が親、以降が連動更新した子）
            val updateResults = mutableListOf<UpdateResult>()

            // 最新バージョンをtargetVersionとして渡してインストール
            installSinglePlugin(name.value, force, useLatest = true, skipIntegrity = skipIntegrity).fold(
                // 型付きエラーをそのまま返す（上流障害は503、メタデータ保存失敗は500など）
                { error -> return error.left() },
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

            // 連動更新: この親に同期している sync: プラグイン（子・孫）を親の新バージョンに追従させる
            // 多段syncでも伝播するよう、直接の子だけでなく子孫全体を親に近い順で処理する
            mpmConfig?.let { config ->
                updateSyncPlugins(
                    mpmConfig = config,
                    syncChildren = config.getSyncDescendants(name.value),
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
     * 管理下プラグインを指定バージョンに切り替える
     *
     * アップグレード・ダウングレードを方向で区別せず、単一の実体として扱う（#405 / #355）。
     *
     * jar・メタデータ・mpm.json はファイルシステムを跨るため単一トランザクションにはできず、
     * 失敗した位置によっては次の中間状態が残りうる。どこまで進んだかは戻り値のエラーメッセージに含める。
     * - メタデータ保存に失敗: jarは新バージョン、メタデータとmpm.jsonは旧バージョンのまま
     * - mpm.json保存に失敗: jarとメタデータは新バージョン、mpm.jsonのバージョン指定は旧値のまま
     *   （ロックファイルも再生成されない）
     * どちらの場合も切り替え前に自動バックアップを作成しているため、
     * `mpm backup restore <id>` で切り替え前の状態へ戻せる（IDはエラーメッセージに含まれる）。
     *
     * また `sync:` で追従している子プラグインの更新に失敗した場合でも、親の切り替え自体は成功として返す。
     * その場合は success=true のまま [UpdateResult.errorMessage] に子の失敗内容を載せる。
     */
    override suspend fun switchVersion(
        name: PluginName,
        version: String,
        force: Boolean,
        skipIntegrity: Boolean
    ): Either<MpmError, UpdateResult> = executeVersionSwitch(name, version, force, skipIntegrity, ACTION_SWITCH)

    /**
     * 管理下プラグインを過去のバージョンへ切り戻す
     *
     * [switchVersion] の薄いラッパー。バージョン省略時のみ履歴から直前のバージョンを解決する。
     */
    override suspend fun rollback(
        name: PluginName,
        version: String?,
        force: Boolean,
        skipIntegrity: Boolean
    ): Either<MpmError, UpdateResult> {
        // バージョンが明示された場合は履歴を参照せずそのまま切り替える
        if (version != null) {
            return executeVersionSwitch(name, version, force, skipIntegrity, ACTION_ROLLBACK)
        }

        // 省略時はメタデータの履歴から「直前のバージョン」を解決する
        val metadata =
            pluginMetadataManager.loadMetadata(name.value).getOrElse {
                return MpmError.PluginError.MetadataNotFound(name.value).left()
            }
        val previousVersion =
            resolvePreviousVersionFromHistory(metadata)
                ?: return MpmError.PluginError
                    .VersionResolutionFailed(
                        name.value,
                        "履歴に切り戻せる過去バージョンがありません"
                    ).left()

        return executeVersionSwitch(name, previousVersion, force, skipIntegrity, ACTION_ROLLBACK)
    }

    /**
     * バージョン切り替えの本体
     *
     * switchVersion / rollback の唯一の実体であり、Mutexの取得もここだけで行う
     * （kotlinx の Mutex は再入不可のため、rollbackからswitchVersionを呼ばずに本メソッドへ集約する）。
     *
     * @param name プラグイン名
     * @param requestedVersion 要求されたバージョン（raw / normalized のどちらでもよい）
     * @param force ロック済み・api-version非互換でも強制するか
     * @param skipIntegrity 整合性検証の不一致を無視するか
     * @param action 履歴に記録するアクション名（"switch" / "rollback"）
     */
    private suspend fun executeVersionSwitch(
        name: PluginName,
        requestedVersion: String,
        force: Boolean,
        skipIntegrity: Boolean,
        action: String
    ): Either<MpmError, UpdateResult> {
        // 並行更新を防止（jar/metadataファイルの競合回避）。update/installAllと同じMutexを共有する
        if (!updateMutex.tryLock()) {
            return MpmError.PluginError.UpdateInProgress.left()
        }
        try {
            val pluginName = name.value

            // mpm.jsonを取得（未初期化とパースエラーを区別する）
            val project = projectRepository.findOrError().getOrElse { return it.left() }

            // 管理下（Managed）でなければ切り替え対象外
            val managedSpec =
                project.getPluginSpec(name) as? PluginSpec.Managed
                    ?: return MpmError.PluginError.NotManaged(pluginName).left()

            // sync:指定は他プラグインへの追従が目的であり、Fixedへ書き換えると同期が壊れる（PinCommandと同じ方針）
            (managedSpec.versionRequirement as? VersionSpecifier.Sync)?.let { sync ->
                return MpmError.PluginError
                    .VersionSwitchNotAllowed(
                        pluginName,
                        "'${sync.targetPlugin}' に同期する設定のため個別にバージョンを変更できません"
                    ).left()
            }

            // メタデータと mpm.json を置き換えられるかを、イベント発火とバックアップ作成より前に検査する（副作用なし）。
            // 未来のスキーマ版数で書かれたファイルは読み込みに成功してしまうため、ここを通さないと
            // 中止が確定している切り替えのために plugins/ ディレクトリ全体のZIPを作り、
            // 起きるはずのない更新を他プラグインへ通知してしまう（Webhookの外部通知は取り消せない）。
            // さらに mpm.json の保存が必ず拒否されるため、末尾の rewriteSpecToFixed だけが失敗し
            // 「jarとメタデータは新バージョン、mpm.json は旧指定」という中間状態が確定的に残る。
            // update(name) / add / remove / uninstall / lock / unlock と同じ位置・同じ理屈で中止する。
            pluginMetadataManager.ensureMetadataReplaceable(pluginName).onLeft {
                return MpmError.PluginError.UpdateFailed(pluginName, it).left()
            }
            projectRepository.ensureSavable().onLeft { reason ->
                return MpmError.PluginError.UpdateFailed(pluginName, reason).left()
            }

            // 現在バージョン・ロック状態をメタデータから取得
            val metadata =
                pluginMetadataManager.loadMetadata(pluginName).getOrElse {
                    return MpmError.PluginError.MetadataNotFound(pluginName).left()
                }
            if (metadata.mpmInfo.settings.lock == true && !force) {
                return MpmError.PluginError.Locked(pluginName).left()
            }
            val currentVersion = metadata.mpmInfo.version.current.raw

            // 対象バージョンをリポジトリ上の実バージョン名（raw）へ解決する。
            // ダウンロード前に解決しておくことで、バックアップ作成前に不正なバージョン指定を弾ける
            val resolvedVersion =
                resolveSwitchTargetVersion(pluginName, metadata, requestedVersion).getOrElse {
                    return it.left()
                }

            // 切り替えをキャンセル可能にするためイベントを発火する（update(name)と同じ扱い）
            val switchEvent =
                BukkitDispatcher.callEventSync(
                    plugin,
                    PluginUpdateEvent(
                        installedPlugin = InstalledPlugin(pluginName),
                        beforeVersion = VersionSpecifier.Fixed(currentVersion),
                        targetVersion = VersionSpecifier.Fixed(resolvedVersion)
                    )
                )
            if (switchEvent.isCancelled) {
                return MpmError.PluginError.OperationCancelled(pluginName, action).left()
            }

            // jarを差し替える前に自動バックアップを作成する（失敗しても切り替え自体は続行する）
            // 途中で失敗した場合の復旧手順を案内するため、作成できたバックアップのIDを控えておく
            var backupId: String? = null
            backupManager.createBackup(BackupReason.UPDATE).fold(
                { error -> plugin.logger.warning("[$action] バックアップ作成失敗: ${error.message} - 処理を続行") },
                { info ->
                    backupId = info.id
                    plugin.logger.info("[$action] バックアップ作成完了: ${info.fileName}")
                }
            )

            // ダウンロード → 整合性検証 → jar差し替え → メタデータ更新 → 履歴追記
            // 失敗時は型付きエラーをそのまま返す（上流障害は503、メタデータ保存失敗は500など）
            installPluginWithVersion(
                pluginName = pluginName,
                expectedVersion = resolvedVersion,
                force = force,
                skipIntegrity = skipIntegrity,
                action = action
            ).getOrElse { return it.left() }

            // mpm.jsonのバージョン指定をFixedへ書き換え、次回のmpm updateで巻き戻らないようにする
            rewriteSpecToFixed(name, resolvedVersion, backupId).getOrElse { return it.left() }

            // sync: で追従している子プラグインを親の新バージョンへ揃える（update(name)と同じ連動更新）。
            // 親だけを切り替えると、アドオンと本体のバージョンが食い違ったまま残ってしまうため。
            // 直接の子だけでなく子孫全体を親に近い順（BFS）で渡し、多段syncでも切り替えを伝播させる。
            val syncResults = mutableListOf<UpdateResult>()
            loadMpmConfig()?.let { config ->
                updateSyncPlugins(
                    mpmConfig = config,
                    syncChildren = config.getSyncDescendants(pluginName),
                    updateResults = syncResults,
                    force = force,
                    skipIntegrity = skipIntegrity
                )
            }
            // 戻り値は親1件のみのため、子の連動結果はログに残して追跡できるようにする
            syncResults.forEach { syncResult ->
                if (syncResult.success) {
                    plugin.logger.info(
                        "[$action] 連動更新: ${syncResult.pluginName} " +
                            "${syncResult.oldVersion} -> ${syncResult.newVersion}"
                    )
                } else {
                    plugin.logger.warning(
                        "[$action] 連動更新に失敗: ${syncResult.pluginName}: ${syncResult.errorMessage}"
                    )
                }
            }

            // ロックファイルを実インストール状態へ追従させる。
            // サービス層で行うことで、コマンド経路とHTTP経路の双方が再生成の恩恵を受ける
            // （再生成はメタデータから作り直す冪等な処理のため、呼び出しが重なっても害はない）。
            lockService.regenerateQuietly(plugin.logger)

            // 子の失敗をログだけに留めると、sync: の不変条件が崩れた状態が成功として確定してしまう。
            // 親の切り替え自体は完了しているため success は true のまま、要約を errorMessage に載せる
            return UpdateResult(
                pluginName = pluginName,
                oldVersion = currentVersion,
                newVersion = resolvedVersion,
                success = true,
                errorMessage = buildSyncFailureMessage(syncResults)
            ).right()
        } finally {
            updateMutex.unlock()
        }
    }

    /**
     * 履歴から「直前にインストールされていたバージョン」を解決する
     *
     * 解決規則は純粋関数 [resolveRollbackTargetVersion] に委譲する
     * （rollbackが必ずより過去へ進むための規則はそちらのKDocを参照）。
     * 履歴に記録されるのは正規化済みバージョンのため、戻り値も正規化済みとなる
     * （実バージョン名への解決は [resolveSwitchTargetVersion] が行う）。
     *
     * @param metadata 対象プラグインのメタデータ
     * @return 直前のバージョン（見つからない場合はnull）
     */
    private fun resolvePreviousVersionFromHistory(metadata: ManagedPluginDto): String? =
        resolveRollbackTargetVersion(
            history = metadata.mpmInfo.history,
            currentNormalized = metadata.mpmInfo.version.current.normalized
        )

    /**
     * 要求されたバージョン文字列をリポジトリ上の実バージョン名（raw）へ解決する
     *
     * web console や履歴からは正規化済みバージョン（例: "5.4.1"）が渡ることがあるが、
     * ダウンロードには実バージョン名（例: "v5.4.1-bukkit"）が必要になる。
     * まず実バージョン名としての解決を試し、失敗した場合のみ全バージョンを正規化して突き合わせる。
     *
     * @param pluginName プラグイン名
     * @param metadata 対象プラグインのメタデータ（リポジトリ情報とversionPatternの取得に使用）
     * @param requestedVersion 要求されたバージョン文字列
     * @return 解決された実バージョン名
     */
    private suspend fun resolveSwitchTargetVersion(
        pluginName: String,
        metadata: ManagedPluginDto,
        requestedVersion: String
    ): Either<MpmError, String> {
        val repositoryInfo = metadata.mpmInfo.repository
        val urlData =
            createUrlData(repositoryInfo.type.name, repositoryInfo.id)
                ?: return MpmError.PluginError.UnsupportedRepository(repositoryInfo.type.name).left()

        // まずはリポジトリ上のバージョン名としてそのまま解決を試みる
        val exactMatch =
            try {
                downloaderRepository.getVersionByName(urlData, requestedVersion)
            } catch (e: Exception) {
                // 実バージョン名として存在しないだけの可能性があるため、ここでは失敗としない
                plugin.logger.fine("[switchVersion] '$requestedVersion' の直接解決に失敗: ${e.message}")
                null
            }
        if (exactMatch != null) {
            return exactMatch.version.right()
        }

        // 見つからない場合は正規化済みバージョンとみなし、全バージョンを正規化して突き合わせる
        val versionPattern = metadata.mpmInfo.versionPattern
        val requestedNormalized = VersionDetail.normalizeWithPattern(requestedVersion, versionPattern)
        val candidates =
            try {
                downloaderRepository.getAllVersions(urlData)
            } catch (e: Exception) {
                // 上流リポジトリの一時障害はクライアントの指定ミスと区別する（HTTPでは503を返す）
                return MpmError.PluginError
                    .UpstreamUnavailable(
                        pluginName,
                        "バージョン一覧の取得に失敗しました: ${e.message}"
                    ).left()
            }

        val matched =
            candidates.firstOrNull { candidate ->
                candidate.version == requestedVersion ||
                    VersionDetail.normalizeWithPattern(candidate.version, versionPattern) == requestedNormalized
            } ?: return MpmError.PluginError
                .VersionResolutionFailed(
                    pluginName,
                    "バージョン '$requestedVersion' はリポジトリに存在しません"
                ).left()

        return matched.version.right()
    }

    /**
     * mpm.jsonのバージョン指定を固定バージョンへ書き換えて保存する
     *
     * mpm.jsonはファイル全体を上書き保存するため、ダウンロード前に読み込んだスナップショットを
     * 使うと、その間に実行された `mpm add` / `mpm remove` の結果を消してしまう。
     * それを避けるため、保存直前に読み直したプロジェクトに対して書き換えを行う。
     *
     * @param name プラグイン名
     * @param version 固定するバージョン
     * @param backupId 切り替え前に作成したバックアップのID（作成できていない場合はnull）
     * @return 成功時はUnit
     */
    private suspend fun rewriteSpecToFixed(
        name: PluginName,
        version: String,
        backupId: String? = null
    ): Either<MpmError, Unit> {
        // 保存直前に最新のmpm.jsonを読み直す（他コマンドによる変更を巻き戻さないため）
        val project = projectRepository.findOrError().getOrElse { return it.left() }

        val newSpec = PluginSpec.Managed(name, VersionSpecifier.Fixed(version))
        val updatedProject = project.updatePlugin(name, newSpec).getOrElse { return it.left() }

        // 保存に失敗した場合、jarとメタデータは既に新バージョンへ差し替わっているため、
        // どこまで進んだのかと復旧手順（バックアップからの復元）をメッセージに含める
        val recoveryHint =
            backupId
                ?.let { "切り替え前に戻す場合は 'mpm backup restore $it' を実行してください。" }
                ?: "mpm.jsonのバージョン指定を手動で確認してください。"
        return projectService.save(updatedProject.withSortedPlugins()).mapLeft { error ->
            MpmError.PluginError.UpdateFailed(
                name.value,
                "jarとメタデータは $version へ差し替え済みですが、mpm.jsonの更新に失敗しました" +
                    "（バージョン指定は旧値のまま、ロックファイルも再生成されていません）: ${error.message}。" +
                    recoveryHint
            )
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
     *
     * ただし `lock` は唯一の拒否権であり、再現インストールでもこれを覆さない。
     * ロック中のプラグインはロックファイルの版と食い違っていても差し替えず、据え置きとして報告する
     * （他サーバーで生成した mpm-lock.yaml を持ち込んだ場合に、ロック中のプラグインだけが
     * 黙って別の版へ動いてしまうのを防ぐため）。この扱いは通常の一括インストール
     * （[planInstallTargets]）と同一で、`--force` でも迂回できない。
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

        // ロックファイルの版に到達しなかったプラグインを記録し、そこへ sync: している子孫を打ち切る。
        // 再現インストールでも「親が動かなければ子も動かない」という sync の不変条件は変わらない。
        // 打ち切らないと、据え置かれた親（1.0.0）に対して子だけがロックの版（2.0.0）へ進み、
        // ロックファイルが記録している整合ペアとも据え置き状態とも一致しない組み合わせが残ってしまう。
        // sortedPlugins はトポロジカル順なので、子を判定する時点で親の結果は必ず確定している。
        val blocklist = SyncFollowBlocklist()

        for (pluginName in sortedPlugins) {
            val expectedVersion = mpmConfig.plugins[pluginName] ?: continue
            // unmanagedはロック対象外なのでスキップ
            if (expectedVersion == "unmanaged") continue

            // ロックにエントリが無い管理下プラグインはドリフトとして失敗扱いにする
            val lockEntry = lock.plugins[pluginName]
            if (lockEntry == null) {
                failed[pluginName] = "ロックファイルにエントリがありません（mpm install で再生成してください）"
                // 版が確定しないため、ここへ追従する子孫も止める
                blocklist.block(pluginName)
                continue
            }

            // 同期先がロックの版に到達しなかった場合は、インストールする前に打ち切る（多段でも伝播する）
            val syncTarget = VersionSpecifierParser.extractSyncTarget(expectedVersion)
            val blockedTarget = blocklist.blockingTargetOf(syncTarget)
            if (blockedTarget != null) {
                failed[pluginName] =
                    "同期先 '$blockedTarget' がロックファイルの版に到達しなかったため、追従インストールをスキップしました"
                blocklist.block(pluginName)
                continue
            }

            // lockは唯一の拒否権なので、再現インストールでもロック中のプラグインには触れない。
            // メタデータを読めない場合はロック状態を判定できないため、通常の一括インストールで
            // 使う InstallCandidate と同じく「ロックされていない」と扱って続行する。
            val installedMetadata = pluginMetadataManager.loadMetadata(pluginName)
            if (installedMetadata.fold({ false }, { it.mpmInfo.settings.lock == true })) {
                val installedVersion = installedMetadata.fold({ null }, { it.mpmInfo.version.current.raw })
                // 版が食い違っている場合だけ報告する。既にロックの版と一致しているなら
                // 何も変わらないため、毎回スキップとして報告しない（planInstallTargets と同じ扱い）
                if (installedVersion != lockEntry.version.raw) {
                    failed[pluginName] = LOCKED_ERROR_MESSAGE
                    // ロックの版に到達していないので、ここへ追従する子孫も止める。
                    // 既にロックの版と一致している場合は「着地済み」なので打ち切らない。
                    blocklist.block(pluginName)
                }
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
                {
                    failed[pluginName] = it.message
                    // 失敗したプラグインの版は確定しないため、追従する子孫も止める
                    blocklist.block(pluginName)
                },
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

        // インストール計画の入力を組み立てる。
        // ディスクI/O（メタデータの読み込み）はここで済ませ、判定そのものは純粋関数に委ねる。
        val candidates =
            sortedPlugins.mapNotNull { pluginName ->
                val expectedVersion = mpmConfig.plugins[pluginName] ?: return@mapNotNull null
                // unmanagedはmpmが版を決めないため、計画にも予定バージョンにも含めない
                if (expectedVersion == "unmanaged") return@mapNotNull null

                val metadataResult = pluginMetadataManager.loadMetadata(pluginName)
                InstallCandidate(
                    pluginName = pluginName,
                    expectedVersion = expectedVersion,
                    installedVersion = metadataResult.fold({ null }, { it.mpmInfo.version.current.raw }),
                    locked = metadataResult.fold({ false }, { it.mpmInfo.settings.lock == true })
                )
            }
        // インストール対象・ロックによる据え置き・予定バージョンをまとめて決める
        // （多段syncが1回のinstallで収束する理由は planInstallTargets のKDocを参照）
        val plan = planInstallTargets(candidates)
        val pluginsToInstall = plan.pluginsToInstall
        val lockedSkipped = plan.lockedSkipped
        // 実際にインストールしたバージョンで更新していくため可変マップへ移す
        val resolvedVersions = plan.resolvedVersions.toMutableMap()
        // 「同期済みなら何もしない」判定でディスク上のバージョンを引くための索引
        val candidatesByName = candidates.associateBy { it.pluginName }

        // インストール結果を記録
        val installed = mutableListOf<PluginInstallInfo>()
        val removed = mutableListOf<PluginRemovalInfo>()
        val failed = mutableMapOf<String, String>()
        // ロックによりスキップしたプラグインもfailedとして報告する。
        // BulkInstallResult には executeUpdate の UpdateResult のような「スキップ」区分が無いため、
        // 現状は failed に載せるしかない（公開APIのバイナリ互換性を保つため区分は追加しない）。
        // そのぶん「実際には何も変わらないのに毎回報告される」ことが無いよう、
        // 据え置きが確定しているロック中のプラグインは planInstallTargets 側で
        // 本当に版がずれている場合だけ lockedSkipped に入れている。
        lockedSkipped.forEach { failed[it] = LOCKED_ERROR_MESSAGE }

        // インストールに失敗したプラグイン名。
        // ここに sync: している子孫は追従すべきバージョンが確定しないため、
        // 誤ったバージョンを入れずにスキップし、その判断を孫まで伝播させる。
        // （ロックでスキップしたプラグインは「現在の版のまま」という確定状態なので含めない）
        val unresolvableSyncTargets = mutableSetOf<String>()

        // 各プラグインをトポロジカルソート順にインストール
        for (pluginName in sortedPlugins) {
            val versionString = mpmConfig.plugins[pluginName] ?: continue

            // 対象外のプラグインの予定バージョンは計画時点で記録済みなので、ここでは何もしない
            if (pluginName !in pluginsToInstall) continue

            val syncTarget = VersionSpecifierParser.extractSyncTarget(versionString)
            val versionToInstall = resolveExpectedVersion(versionString, resolvedVersions)

            // 同期先が latest / tag: の場合、計画時点では着地バージョンが分からないため
            // 子孫を保守的にインストール対象へ入れている。ここまで来れば親の実際のバージョンが
            // 確定しているので、既に同期済みだと分かった子は何もせずに済ませる
            // （毎回 sync ツリー全体を再取得しないため。連動更新の updateSyncPlugins と同じ扱い）。
            //
            // この判定は同期先の失敗判定より必ず前に置く。後ろに置くと、保守的に対象へ入れただけで
            // 「そもそもインストール不要（＝親と同じ版で既に同期済み）」な子まで失敗として報告され、
            // その偽の失敗が孫にまで連鎖してしまう。
            // 親が失敗しても resolvedVersions[親] は計画時点のディスク上バージョンのままなので、
            // ここで一致する子は本当に何もする必要が無い子だけである。
            if (syncTarget != null && candidatesByName[pluginName]?.installedVersion == versionToInstall) {
                resolvedVersions[pluginName] = versionToInstall
                continue
            }

            // 同期先が確定していない場合は追従インストールを行わない（多段でも打ち切る）。
            // ここに来るのは「親が動くはずなのに動かなかったため、追うべき版が分からない」子だけである。
            if (syncTarget != null && syncTarget in unresolvableSyncTargets) {
                failed[pluginName] = "同期先 '$syncTarget' のインストールに失敗したため、追従インストールをスキップしました"
                unresolvableSyncTargets.add(pluginName)
                continue
            }

            installPluginWithVersion(pluginName, versionToInstall, force, skipIntegrity).fold(
                {
                    failed[pluginName] = it.message
                    // 失敗したプラグインのバージョンは確定しないため、追従する子孫も止める
                    unresolvableSyncTargets.add(pluginName)
                },
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

        // メタデータを書き換えられるかをイベント発火より前に検査する（副作用なし）。
        // 未来のスキーマ版数のファイルは読み込みには成功するため、保存時のガードだけに頼ると
        // 「イベントは飛んだのに保存は拒否された」状態になり、通知を受けた外部システムだけが
        // ロック済みだと認識する食い違いが残る。add / uninstall / install と同じ順序に揃える
        pluginMetadataManager.ensureMetadataReplaceable(name.value).onLeft {
            return MpmError.PluginError.MetadataSaveFailed(name.value, it).left()
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

        // メタデータを書き換えられるかをイベント発火より前に検査する（副作用なし）。
        // 理由は lock と同じで、保存が拒否されるのにイベントだけが飛ぶ食い違いを防ぐ
        pluginMetadataManager.ensureMetadataReplaceable(name.value).onLeft {
            return MpmError.PluginError.MetadataSaveFailed(name.value, it).left()
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
     * 多段 sync では、途中のノードがロック・破損・失敗で据え置かれた場合、
     * その先の子孫も追従させない（[SyncFollowBlocklist]）。
     * 中間ノードのバージョンが動いていない以上、孫だけを進めると
     * 「親が更新されなければ子も更新されない」という仕様に反するためである。
     *
     * @param mpmConfig mpm.json の設定（sync ターゲット解決に使用）
     * @param syncChildren 連動更新の対象とする sync: プラグイン名の集合。
     *   打ち切り判定が成立するよう、親に近い順（BFS / トポロジカル順）で渡すこと。
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

        // 追従しなかったノードを記録し、その先の子孫まで打ち切るための状態
        val blocklist = SyncFollowBlocklist()

        for (childName in syncChildren) {
            // 子の同期先（親プラグイン名）を mpm.json の sync: 指定から特定する
            val syncTarget =
                mpmConfig.plugins[childName]?.let { VersionSpecifierParser.extractSyncTarget(it) }

            // 同期先が追従しなかった場合は、バージョン比較より前に打ち切る。
            // ここを先頭で判定することで、たまたま版が一致していても孫へ伝播させない。
            val blockedTarget = blocklist.blockingTargetOf(syncTarget)
            if (blockedTarget != null) {
                val heldVersion =
                    pluginMetadataManager
                        .loadMetadata(childName)
                        .fold({ "unknown" }, { it.mpmInfo.version.current.raw })
                progressCallback?.invoke("<gray>[$childName] <yellow>同期先が更新されなかったためスキップ")
                updateResults.add(
                    UpdateResult(
                        pluginName = childName,
                        oldVersion = heldVersion,
                        newVersion = heldVersion,
                        success = false,
                        errorMessage = "同期先 '$blockedTarget' が更新されなかったため",
                        skipped = true
                    )
                )
                // 自身も追従しなかったので、さらに下の子孫も打ち切る
                blocklist.block(childName)
                continue
            }

            // 同期先が mpm.json の管理下にあるかを、メタデータを読むより前に確かめる。
            // mpm.json から消えた（あるいは unmanaged になった）同期先のメタデータは
            // アンインストール後もディスクに残るため、これを見てしまうと
            // 「管理対象から外れたプラグインの古い版に、生きているプラグインを引きずり込む」ことになる。
            // cron の警告（warnBrokenSyncGraph）が「追従更新は行われません」と伝えている状態と挙動を揃える
            val syncTargetSpec = syncTarget?.let { mpmConfig.plugins[it] }
            val syncTargetIsManaged = syncTargetSpec != null && syncTargetSpec != "unmanaged"

            // 親の（更新後の）インストール済みバージョンを解決する
            val targetVersion =
                syncTarget
                    ?.takeIf { syncTargetIsManaged }
                    ?.let { parent ->
                        pluginMetadataManager.loadMetadata(parent).fold({ null }, { it.mpmInfo.version.current.raw })
                    }

            // 子の現在バージョンとロック状態を取得
            val childMetadata = pluginMetadataManager.loadMetadata(childName)
            val currentVersion = childMetadata.fold({ "unknown" }, { it.mpmInfo.version.current.raw })

            // 親のバージョンを解決できない場合はスキップ（管理外の同期先、親のメタデータ欠落など）
            if (targetVersion == null) {
                // 原因が分かるよう「管理外」と「解決できない」を区別して伝える
                val reason =
                    if (syncTarget != null && !syncTargetIsManaged) {
                        "同期先 '$syncTarget' がmpm.jsonに存在しないため、追従更新を行いませんでした"
                    } else {
                        "同期先 '${syncTarget ?: "?"}' のバージョンを解決できませんでした"
                    }
                progressCallback?.invoke("<gray>[$childName] <yellow>$reason")
                updateResults.add(
                    UpdateResult(
                        pluginName = childName,
                        oldVersion = currentVersion,
                        newVersion = currentVersion,
                        success = false,
                        errorMessage = reason
                    )
                )
                // 追従先が分からない以上この子は据え置かれるため、その先の子孫も打ち切る
                blocklist.block(childName)
                continue
            }

            // メタデータファイルが在るのに読めない場合はロック状態を判定できない。
            // 「読めない＝ロックされていない」と楽観視するとロック済みプラグインを無人更新してしまうため、
            // スケジューラのLockState.UNKNOWNと同じく安全側（更新しない）に倒す。
            if (childMetadata.isLeft() && metadataFileExists(childName)) {
                progressCallback?.invoke("<gray>[$childName] <yellow>メタデータを読み込めないためスキップ")
                updateResults.add(
                    UpdateResult(
                        pluginName = childName,
                        oldVersion = currentVersion,
                        newVersion = currentVersion,
                        success = false,
                        errorMessage = METADATA_UNREADABLE_ERROR_MESSAGE
                    )
                )
                // ロック状態を確認できず据え置いたため、その先の子孫も打ち切る
                blocklist.block(childName)
                continue
            }

            // 既に親のバージョンに同期済みなら再取得しない
            // （ロック判定より前に行うことで、更新不要なロック済みの子を誤って失敗扱いにしない）
            // これは「追従が完了している正常な状態」であり据え置きではないため、blocklistには入れない。
            // ここで打ち切ると、中間ノードがたまたま親と一致していた場合に孫が永久に取り残される。
            if (targetVersion == currentVersion) {
                continue
            }

            // ロックされている場合はスキップ（現状維持）
            // lockは唯一の拒否権であり、親が更新されても子は更新しない
            if (childMetadata.fold({ false }, { it.mpmInfo.settings.lock == true })) {
                progressCallback?.invoke("<gray>[$childName] <yellow>ロック中のためスキップ")
                updateResults.add(
                    UpdateResult(
                        pluginName = childName,
                        oldVersion = currentVersion,
                        newVersion = currentVersion,
                        success = false,
                        errorMessage = LOCKED_ERROR_MESSAGE,
                        skipped = true
                    )
                )
                // lockは唯一の拒否権であり、この子は旧バージョンのまま据え置かれる。
                // その先の孫まで更新すると孫だけが先行してしまうため、打ち切る。
                blocklist.block(childName)
                continue
            }

            // 親のバージョンを子のリポジトリから取得して置換する
            progressCallback?.invoke(
                "<gray>[$childName] $currentVersion → $targetVersion 連動更新をダウンロード中..."
            )
            // sync連動更新は無人実行のため、破損メタデータを作り直して lock を失うことがないよう中断させる
            installPluginWithVersion(
                pluginName = childName,
                expectedVersion = targetVersion,
                force = force,
                skipIntegrity = skipIntegrity,
                abortOnUnreadableMetadata = true
            ).fold(
                // インストール失敗時
                { error ->
                    progressCallback?.invoke("<gray>[$childName] <red>連動更新失敗: ${error.message}")
                    // 失敗した子は旧バージョンのままなので、その先の子孫も打ち切る
                    blocklist.block(childName)
                    updateResults.add(
                        UpdateResult(
                            pluginName = childName,
                            oldVersion = currentVersion,
                            newVersion = targetVersion,
                            success = false,
                            errorMessage = "連動更新に失敗: ${error.message}"
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
     * 更新経路の失敗理由を型付きエラーへ包む
     *
     * 内部処理の失敗理由は文字列で組み立てているが、サービス境界では型付きの [MpmError] が必要になる
     * （HTTPステータスへのマッピングが型で決まるため）。包み方を1箇所に集約するためのヘルパー。
     *
     * @param pluginName 対象プラグイン名
     * @param reason 失敗理由
     */
    private fun updateFailure(
        pluginName: String,
        reason: String
    ): MpmError = MpmError.PluginError.UpdateFailed(pluginName, reason)

    /**
     * インストール経路の失敗理由を型付きエラーへ包む
     *
     * 役割は [updateFailure] と同じで、履歴・メッセージ上の文脈がインストールの場合に使う。
     *
     * @param pluginName 対象プラグイン名
     * @param reason 失敗理由
     */
    private fun installFailure(
        pluginName: String,
        reason: String
    ): MpmError = MpmError.PluginError.InstallFailed(pluginName, reason)

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
    ): Either<MpmError, InstallResult> {
        val metadataDir = pluginDirectory.getMetadataDirectory()
        val metadataFile = File(metadataDir, "$pluginName.yaml")

        if (!metadataFile.exists()) {
            return updateFailure(pluginName, "メタデータファイルが見つかりません: $pluginName.yaml").left()
        }

        val metadata =
            try {
                val yamlString = metadataFile.readText()
                Yaml.default.decodeFromString(ManagedPluginDto.serializer(), yamlString)
            } catch (e: Exception) {
                return updateFailure(pluginName, "メタデータの読み込みに失敗しました: ${e.message}").left()
            }

        // メタデータを保存できるかを、ダウンロードやJARの差し替えより前に検査する（副作用なし）。
        // schemaVersion は単なるIntフィールドなので、未来版数(v3など)のファイルでも読み込みは成功する。
        // 保存地点のガードだけに頼ると、新JARを配置して旧JARを削除した後に保存が拒否され、
        // 「JARだけ更新されメタデータは旧版のまま」という状態が残ってしまう（cron自動更新でも起こりうる）。
        pluginMetadataManager.ensureMetadataReplaceable(pluginName).onLeft {
            return updateFailure(pluginName, it).left()
        }

        val mpmInfoDto = metadata.mpmInfo
        val pluginInfoDto = metadata.pluginInfo
        val repositoryInfo = mpmInfoDto.repository

        val urlData =
            createUrlData(repositoryInfo.type.name, repositoryInfo.id)
                ?: return updateFailure(
                    pluginName,
                    "未対応のリポジトリタイプです: ${repositoryInfo.type.name}"
                ).left()

        // mpm.jsonからtag指定を取得（tag:指定の場合はチャンネル別の最新を取得する）
        val mpmConfig = loadMpmConfig()
        val versionString = mpmConfig?.plugins?.get(pluginName)
        val tagChannelForPlugin = versionString?.let { VersionSpecifierParser.extractTag(it) }

        // mpm.jsonがFixed指定の場合、更新時もリポジトリの最新ではなくその指定バージョンへ揃える。
        // pin / rollback で固定したバージョンを尊重するための扱いで、
        // Fixedを動的解決の対象外とする checkOutdated / resolveExpectedVersion と方針を揃える。
        val fixedVersionForPlugin =
            versionString
                ?.let { VersionSpecifierParser.parse(it) as? VersionSpecifier.Fixed }
                ?.version

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
                    ) ?: return updateFailure(
                        pluginName,
                        "tag '$tagChannelForPlugin' に該当するバージョンが見つかりません: $pluginName"
                    ).left()
                } else {
                    ChannelVersionResolver.resolveLatest(
                        downloaderRepository,
                        urlData,
                        matchingRepositoryConfig
                    )
                }
            } catch (e: Exception) {
                return updateFailure(pluginName, "最新バージョン情報の取得に失敗しました: ${e.message}").left()
            }

        // useLatestの場合は最新バージョン（Fixed指定時はその固定バージョン）でDL、
        // そうでなければメタデータの現在バージョンでDL
        val versionData =
            if (useLatest) {
                if (fixedVersionForPlugin != null) {
                    try {
                        downloaderRepository.getVersionByName(urlData, fixedVersionForPlugin)
                    } catch (e: Exception) {
                        return updateFailure(
                            pluginName,
                            "指定されたバージョン '$fixedVersionForPlugin' の取得に失敗しました: ${e.message}"
                        ).left()
                    }
                } else {
                    latestVersionData
                }
            } else {
                VersionData(mpmInfoDto.download.downloadId, mpmInfoDto.version.current.raw)
            }
        val action = if (useLatest) "update" else "install"

        // メタデータを更新（最新バージョン情報を反映）
        val updatedMetadataWithLatest =
            pluginMetadataManager
                .updateMetadata(pluginName, versionData, latestVersionData, action)
                .getOrElse { return MpmError.PluginError.MetadataSaveFailed(pluginName, it).left() }

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
            return MpmError.PluginError.OperationCancelled(pluginName, action).left()
        }

        val downloadedFile =
            try {
                downloaderRepository.downloadByVersion(
                    urlData,
                    versionData,
                    mpmInfoDto.fileNamePattern
                )
            } catch (e: PluginDownloadException) {
                // 型付きのダウンロード失敗は文字列へ潰さずに伝播させる。
                // 上流の429/5xxはUpstreamUnavailableとなり、HTTPでは再試行可能な503になる
                return e.toMpmError(pluginName).left()
            } catch (e: Exception) {
                return updateFailure(pluginName, "プラグインのダウンロードに失敗しました: ${e.message}").left()
            }

        if (downloadedFile == null) {
            return updateFailure(pluginName, "プラグインファイルのダウンロードに失敗しました。").left()
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
            ).getOrElse { return updateFailure(pluginName, it).left() }

        // tempファイルに対してAPIバージョンと依存関係の事前チェックを行う
        // チェックに失敗した場合はtempファイルを削除して早期リターン
        validateDownloadedPlugin(downloadedFile, pluginName, force).onLeft { error ->
            downloadedFile.delete()
            return updateFailure(pluginName, error).left()
        }

        // 更新後のメタデータからバージョン情報を取得してファイル名を生成
        val template = mpmInfoDto.fileNameTemplate ?: "<pluginInfo.name>-<mpmInfo.version.current.normalized>.jar"
        val updatedVersion = updatedMetadataWithLatest.mpmInfo.version.current.normalized
        val newFileName = generateFileName(template, pluginInfoDto.name, updatedVersion)

        // staged copy: 配置先と同じディレクトリの一時ファイル経由でアトミックに置換する
        // 途中で失敗しても既存JARが壊れず、中間ファイルも残らない
        val pluginsDir = pluginDirectory.getPluginsDirectory()
        val targetFile = File(pluginsDir, newFileName)
        replaceJarAtomically(downloadedFile, targetFile).getOrElse { reason ->
            return updateFailure(pluginName, reason).left()
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
        pluginMetadataManager
            .saveMetadata(pluginName, mergeLatestSettings(pluginName, updatedMetadata))
            .getOrElse {
                // ここに到達した時点でjarは既に新バージョンへ差し替わっているため、
                // メタデータだけが旧バージョンのまま残る。手動確認が必要であることを明示する
                return MpmError.PluginError
                    .MetadataSaveFailed(
                        pluginName,
                        "jarは既に $newFileName へ差し替え済みですが、メタデータの保存に失敗しました" +
                            "（plugins/ と metadata の内容を手動で確認してください）: $it"
                    ).left()
            }

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
     *
     * @param action メタデータの履歴に記録するアクション名（"install" / "switch" / "rollback" など）
     * @param abortOnUnreadableMetadata メタデータファイルが存在するのに読み込めない場合に処理を中断するか。
     *   自動更新（cron / sync連動）のような無人実行では、破損メタデータを作り直すと
     *   `lock: true` などの設定を無音で失うため true を指定して中断する。
     *   一方 `mpm install` のようなユーザー起点の操作では、破損メタデータの作り直しが
     *   復旧手段そのものになるため false（既定）のままにして続行させる。
     *   ただし続行する場合も、原本は `.corrupt` へ退避してから作り直す
     *   （[PluginMetadataManager.quarantineMetadata]）。
     */
    private suspend fun installPluginWithVersion(
        pluginName: String,
        expectedVersion: String,
        force: Boolean = false,
        skipIntegrity: Boolean = false,
        expectedSha256: String? = null,
        action: String = "install",
        abortOnUnreadableMetadata: Boolean = false
    ): Either<MpmError, InstallResult> {
        // メタデータを保存できるかを、ダウンロードやJARの差し替えより前に検査する（副作用なし）。
        // 未来のスキーマ版数で書かれたファイルは読み込みに成功してしまうため、
        // 読み込み失敗時だけの判定では取りこぼす。破壊的操作に入る前に無条件で中止する。
        pluginMetadataManager.ensureMetadataReplaceable(pluginName).onLeft {
            return installFailure(pluginName, it).left()
        }

        // リポジトリファイルを取得
        val repositoryFile =
            repositoryManager.getRepositoryFile(pluginName)
                ?: return installFailure(pluginName, "リポジトリファイルが見つかりません: $pluginName").left()

        val firstRepository =
            repositoryFile.repositories.firstOrNull()
                ?: return installFailure(pluginName, "リポジトリ設定が見つかりません: $pluginName").left()

        // UrlDataを作成
        val urlData =
            createUrlData(firstRepository.type, firstRepository.repositoryId)
                ?: return installFailure(pluginName, "未対応のリポジトリタイプです: ${firstRepository.type}").left()

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
                    ) ?: return installFailure(
                        pluginName,
                        "tag '$tagChannel' に該当するバージョンが見つかりません: $pluginName"
                    ).left()
                } else {
                    ChannelVersionResolver.resolveLatest(
                        downloaderRepository,
                        urlData,
                        firstRepository
                    )
                }
            } catch (e: Exception) {
                return installFailure(pluginName, "バージョン情報の取得に失敗しました: ${e.message}").left()
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
                    return installFailure(
                        pluginName,
                        "指定されたバージョン '$expectedVersion' の取得に失敗しました: ${e.message}"
                    ).left()
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

        // 読み込めなかったメタデータを退避する必要がある場合、その原因を保持しておく。
        // 実際の退避は最後の saveMetadata の直前まで遅延させる（理由は退避処理の箇所を参照）。
        var pendingQuarantineReason: String? = null

        val metadata =
            pluginMetadataManager.loadMetadata(pluginName).fold(
                // メタデータが存在しない場合は新規作成
                { loadError ->
                    // 未来のスキーマ版数で書かれたファイルは「破損」ではなく「このmpmでは解釈できないだけ」。
                    // 退避して作り直すと有効な設定（lockなど）と未知フィールドを現行版数へ巻き戻すことになり、
                    // saveMetadata のダウングレード防止ガードを退避経由で迂回してしまう。
                    // ダウンロードやJARの差し替えより前に判定し、無条件に中断する。
                    pluginMetadataManager.ensureMetadataReplaceable(pluginName).onLeft {
                        return installFailure(pluginName, it).left()
                    }

                    // ファイルが在るのに読めない場合は破損・未知フィールドなどが原因であり、
                    // ここで作り直すと既存の settings（lockなど）を無音で失う。
                    // 無人実行（自動更新・sync連動）ではデータを壊さないことを優先して中断するが、
                    // ユーザー起点のインストールでは作り直しが復旧手段になるため続行する。
                    if (abortOnUnreadableMetadata && metadataFileExists(pluginName)) {
                        return installFailure(
                            pluginName,
                            "メタデータを読み込めないため処理を中断しました: $loadError"
                        ).left()
                    }
                    // 続行する場合も、破損ファイルを黙って上書きすると lock などの設定が
                    // 復旧不能になるため、作り直す前に必ず退避する。
                    // ただしここでは退避せず、原因の記録だけに留める。
                    // この時点で原本を消すと、後続のダウンロード失敗などで中断した際に
                    // 「原本は退避済み・作り直しは未保存」というメタデータ不在の状態が残り、
                    // メタデータファイルの存在を前提とする無人経路のロック判定が無効化されてしまう。
                    pendingQuarantineReason = loadError
                    pluginMetadataManager
                        .createMetadata(pluginName, firstRepository, versionData, action, resolvedChannel)
                        .getOrElse { return MpmError.PluginError.MetadataSaveFailed(pluginName, it).left() }
                },
                // メタデータが存在する場合は更新
                {
                    pluginMetadataManager
                        .updateMetadata(pluginName, versionData, latestVersionData, action)
                        .getOrElse { return MpmError.PluginError.MetadataSaveFailed(pluginName, it).left() }
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
            } catch (e: PluginDownloadException) {
                // 型付きのダウンロード失敗は文字列へ潰さずに伝播させる。
                // 上流の429/5xxはUpstreamUnavailableとなり、HTTPでは再試行可能な503になる
                return e.toMpmError(pluginName).left()
            } catch (e: Exception) {
                return installFailure(pluginName, "プラグインのダウンロードに失敗しました: ${e.message}").left()
            }

        if (downloadedFile == null) {
            return installFailure(pluginName, "プラグインファイルのダウンロードに失敗しました。").left()
        }

        // frozenインストール時は、ロックファイルに記録されたsha256を最優先で照合する。
        // リポジトリ側でアーティファクトが同じバージョン名のまま差し替えられていても検出でき、
        // バイト単位の再現性（npm ci相当）を保証する。--skip-integrityでも省略しない（frozenの本質のため）。
        if (expectedSha256 != null) {
            val actualSha256 = integrityVerifier.computeSha256(downloadedFile)
            if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                downloadedFile.delete()
                return installFailure(
                    pluginName,
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
            ).getOrElse { return installFailure(pluginName, it).left() }

        // tempファイルに対してAPIバージョンと依存関係の事前チェックを行う
        // チェックに失敗した場合はtempファイルを削除して早期リターン
        validateDownloadedPlugin(downloadedFile, pluginName, force).onLeft { error ->
            downloadedFile.delete()
            return installFailure(pluginName, error).left()
        }

        // ファイル名を生成
        val template = firstRepository.fileNameTemplate ?: "<pluginInfo.name>-<mpmInfo.version.current.normalized>.jar"
        val newFileName = generateFileName(template, pluginName, metadata.mpmInfo.version.current.normalized)

        // staged copy: 配置先と同じディレクトリの一時ファイル経由でアトミックに置換する
        // 途中で失敗しても既存JARが壊れず、中間ファイルも残らない
        val pluginsDir = pluginDirectory.getPluginsDirectory()
        val targetFile = File(pluginsDir, newFileName)
        replaceJarAtomically(downloadedFile, targetFile).getOrElse { reason ->
            return installFailure(pluginName, reason).left()
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

        // 破損メタデータの退避は、ここまでの処理がすべて成功した保存の直前で初めて行う。
        // 退避できない場合は原本を守るためインストールを失敗として扱う。
        // 退避先は保存に失敗したときに戻すため保持しておく。
        var quarantinedFile: File? = null
        pendingQuarantineReason?.let { loadError ->
            pluginMetadataManager.quarantineMetadata(pluginName).fold(
                { quarantineError ->
                    return installFailure(
                        pluginName,
                        "破損したメタデータを退避できなかったため処理を中断しました: " +
                            "$quarantineError (元のエラー: $loadError)"
                    ).left()
                },
                { quarantined ->
                    quarantinedFile = quarantined
                    // 退避が発生した場合のみ、復旧できるように退避先を明示して警告する
                    quarantined?.let {
                        plugin.logger.warning(
                            "メタデータを読み込めないため退避して作り直します: $pluginName " +
                                "($loadError) -> ${it.absolutePath}"
                        )
                    }
                }
            )
        }

        // 長時間のダウンロード中に実行された lock/unlock を上書きで失わないよう、
        // 保存直前に読み直した settings を引き継ぐ（mergeLatestSettings）。
        // 直前に退避が起きていた場合は原本が無いため no-op に縮退するだけで害はない。
        pluginMetadataManager
            .saveMetadata(pluginName, mergeLatestSettings(pluginName, updatedMetadata))
            .onLeft { saveError ->
                // 退避した原本を戻さないと `metadata/<名前>.yaml` が不在のまま残り、
                // ロック判定などが無音で無効化されてしまう（詳細は restoreQuarantinedMetadataOrWarn のKDoc）
                val restoreNote =
                    restoreQuarantinedMetadataOrWarn(
                        metadataManager = pluginMetadataManager,
                        logger = plugin.logger,
                        pluginName = pluginName,
                        quarantinedFile = quarantinedFile
                    )
                // ここに到達した時点でjarは既に新バージョンへ差し替わっているため、
                // メタデータだけが旧バージョンのまま残る。手動確認が必要であることを明示する
                return MpmError.PluginError
                    .MetadataSaveFailed(
                        pluginName,
                        "jarは既に $newFileName へ差し替え済みですが、メタデータの保存に失敗しました" +
                            "（plugins/ と metadata の内容を手動で確認してください）: $saveError$restoreNote"
                    ).left()
            }

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
     * 保存直前に最新のメタデータを読み直し、設定（lock等）だけを引き継ぐ
     *
     * メタデータはファイル全体を上書き保存するため、ダウンロード開始前に読み込んだスナップショットを
     * そのまま保存すると、数十秒かかるダウンロードの最中に実行された `mpm lock` / `mpm unlock` の
     * 結果を消してしまう（HTTPは200を返したのにロックされていない状態になる）。
     * mpm.jsonに対して [rewriteSpecToFixed] が採っているのと同じ方針で、保存直前に読み直す。
     * バージョンやダウンロード情報は本処理が確定させた値が正しいため、マージ対象は設定のみとする。
     *
     * @param pluginName プラグイン名
     * @param metadata 保存しようとしているメタデータ
     * @return 最新の設定を反映したメタデータ（読み直しに失敗した場合は元のメタデータ）
     */
    private fun mergeLatestSettings(
        pluginName: String,
        metadata: ManagedPluginDto
    ): ManagedPluginDto {
        val latestSettings =
            pluginMetadataManager
                .loadMetadata(pluginName)
                .getOrNull()
                ?.mpmInfo
                ?.settings
                ?: return metadata
        return metadata.copy(mpmInfo = metadata.mpmInfo.copy(settings = latestSettings))
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
     * メタデータファイルが存在するかどうかを判定する
     *
     * [PluginMetadataManager.loadMetadata] は「ファイルが無い」と「読めない（破損・未知フィールド）」を
     * 同じ失敗として返すため、この2つを区別するために使う。
     *
     * @param pluginName プラグイン名
     * @return metadata/<プラグイン名>.yaml が存在する場合はtrue
     */
    private fun metadataFileExists(pluginName: String): Boolean =
        File(pluginDirectory.getMetadataDirectory(), "$pluginName.yaml").exists()

    /**
     * バージョン指定文字列を実際のバージョンに解決する
     *
     * latest / tag: のような動的指定は、ここでは解決せず指定文字列のまま返す。
     * 解決先は問い合わせのたびに変わりうるため、実際のチャンネル解決は
     * [installPluginWithVersion]（[ChannelVersionResolver]）に一本化する。
     * ここでメタデータの現在バージョンへ潰してしまうと、`mpm install` が
     * 常に「今入っている版」を取り直すだけになり、動的指定が永久に上がらなくなる（#283）。
     *
     * sync:指定は [resolved] に記録済みの同期先の解決値を引く。多段syncでも
     * トポロジカル順に処理していれば同期先は必ず先に記録されているため、
     * 文字列 "sync:X" がそのまま返るのは呼び出し順が壊れている場合だけである。
     */
    private fun resolveExpectedVersion(
        expected: String,
        resolved: Map<String, String>
    ): String {
        val syncTarget = VersionSpecifierParser.extractSyncTarget(expected)
        return when {
            syncTarget != null -> resolved[syncTarget] ?: expected
            // latest / tag: は動的解決が必要なため、指定文字列のまま委譲する
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