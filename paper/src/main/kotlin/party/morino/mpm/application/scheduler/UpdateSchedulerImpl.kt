/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.scheduler

import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.application.lock.LockService
import party.morino.mpm.api.application.model.UpdateResult
import party.morino.mpm.api.application.model.outdated.OutdatedInfo
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.application.plugin.PluginUpdateService
import party.morino.mpm.api.application.scheduler.UpdateScheduler
import party.morino.mpm.api.domain.backup.ServerBackupManager
import party.morino.mpm.api.domain.config.ConfigManager
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.domain.plugin.model.VersionSpecifierParser
import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import party.morino.mpm.api.domain.project.dto.MpmConfig
import party.morino.mpm.api.domain.project.dto.detectCircularDependencies
import party.morino.mpm.api.domain.project.dto.getSyncDependencies
import party.morino.mpm.api.domain.project.repository.ProjectRepository
import party.morino.mpm.api.model.backup.BackupReason
import party.morino.mpm.api.shared.error.MpmError
import party.morino.mpm.utils.regenerateQuietly
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * cron式に基づくプラグイン自動更新スケジューラーの実装
 *
 * nextExecution()で次回実行時刻を正確に計算し、
 * BukkitSchedulerのrunTaskLaterAsynchronouslyで正確にスケジュールする
 *
 * 自動更新の対象は「mpm.jsonのバージョン指定が動的（latest / tag:）かつ非ロック」のものだけで、
 * 固定バージョンやロック済みのプラグインはチェックして報告するのみで更新しない。
 * sync:指定のプラグインは親の更新に連動して更新される（連動処理は [PluginUpdateService] の責務）。
 */
class UpdateSchedulerImpl :
    UpdateScheduler,
    KoinComponent {
    // Koinによる依存性注入
    private val plugin: JavaPlugin by inject()
    private val configManager: ConfigManager by inject()
    private val updateService: PluginUpdateService by inject()
    private val infoService: PluginInfoService by inject()
    private val pluginMetadataManager: PluginMetadataManager by inject()
    private val lockService: LockService by inject()

    // cron 1回につき1度だけ更新前バックアップを作成するために使用する
    private val backupManager: ServerBackupManager by inject()

    // mpm.jsonのバージョン指定（latest / tag: / sync: / 固定）を読むために使用する
    private val projectRepository: ProjectRepository by inject()

    // スケジューラータスクの参照（停止用）
    private var schedulerTask: BukkitTask? = null

    // 非同期処理用のCoroutineScope（start/stop時に再生成される）
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // スケジューラーの実行状態フラグ（stop後の再スケジュールを防止）
    @Volatile
    private var running = false

    // 世代カウンター（restart時に旧世代のタスクが新世代に混入するのを防止）
    private val generation = AtomicLong(0)

    // cron式パーサー（UNIX形式: 分 時 日 月 曜日）
    private val cronParser =
        CronParser(
            CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX)
        )

    /**
     * スケジューラーを開始する
     *
     * schedule設定が無効の場合は何もしない
     * 既に実行中の場合は一度停止してから再開する
     */
    override fun start() {
        // 既存のスケジュールを停止してクリーンな状態にする
        stop()

        val scheduleConfig = configManager.getConfig().settings.schedule
        if (!scheduleConfig.enabled) {
            plugin.logger.info("Scheduled auto-update is disabled.")
            return
        }

        // cron式のバリデーション
        val cron =
            try {
                cronParser.parse(scheduleConfig.cron).validate()
            } catch (e: Exception) {
                plugin.logger.warning("Invalid cron expression '${scheduleConfig.cron}': ${e.message}")
                return
            }

        // 新しいCoroutineScopeを作成し、世代を更新
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        running = true
        val currentGeneration = generation.incrementAndGet()

        // 起動時チェック
        if (scheduleConfig.checkOnStartup) {
            runStartupCheck()
        }

        // 次回実行時刻を計算してスケジュール
        val executionTime = ExecutionTime.forCron(cron)
        scheduleNext(executionTime, currentGeneration)

        plugin.logger.info("Scheduled auto-update started with cron: '${scheduleConfig.cron}'")
    }

    /**
     * スケジューラーを停止する
     *
     * BukkitTaskをキャンセルし、CoroutineScopeを破棄する
     */
    override fun stop() {
        running = false
        schedulerTask?.cancel()
        schedulerTask = null
        scope.cancel()
    }

    /**
     * スケジューラーを再起動する
     *
     * 設定ファイルの再読み込み後に呼び出すことで、新しいcron設定を反映する
     */
    override fun restart() {
        plugin.logger.info("Restarting update scheduler...")
        start()
    }

    /**
     * 起動時の更新チェックを実行する
     *
     * cron実行時と同じ分類・同じ表示でチェック結果を出力する（更新は一切行わない）
     */
    private fun runStartupCheck() {
        scope.launch {
            val prefix = STARTUP_PREFIX
            plugin.logger.info("$prefix Checking for plugin updates on startup...")
            val specs = loadVersionSpecs(prefix)
            val (classification, hasCheckErrors) = runCheck(prefix, specs) ?: return@launch
            reportClassification(prefix, classification, specs, hasCheckErrors)
        }
    }

    /**
     * 次回実行時刻を計算し、BukkitSchedulerでスケジュールする
     *
     * 次回予約を先に行い、その後に実処理を実行する
     * 世代管理により、restart後に旧世代のタスクが混入することを防止する
     *
     * @param executionTime cron実行時刻の計算オブジェクト
     * @param expectedGeneration このスケジュールが属する世代
     */
    private fun scheduleNext(
        executionTime: ExecutionTime,
        expectedGeneration: Long
    ) {
        // 停止済みまたは世代不一致の場合は再スケジュールしない
        if (!running || generation.get() != expectedGeneration) return

        val now = ZonedDateTime.now()
        val nextOpt = executionTime.nextExecution(now)
        if (!nextOpt.isPresent) {
            plugin.logger.warning("Could not calculate next execution time for cron expression.")
            return
        }

        val next = nextOpt.get()
        // ミリ秒単位の遅延をtickに変換（切り上げで早発を防止、1 tick = 50ms）
        val delayMs = ChronoUnit.MILLIS.between(now, next)
        val delayTicks = maxOf((delayMs + 49) / 50, 1L)

        plugin.logger.info("Next scheduled update: $next (in ${delayMs / 1000}s)")

        // 指定時刻に一回限りのタスクを登録
        schedulerTask =
            plugin.server.scheduler.runTaskLaterAsynchronously(
                plugin,
                Runnable {
                    // 停止フラグと世代を再チェック（stop/restartが呼ばれている可能性）
                    if (!running || generation.get() != expectedGeneration) return@Runnable

                    // 次回予約を処理の前に行う（長い更新でスロットを飛ばさない）
                    scheduleNext(executionTime, expectedGeneration)

                    scope.launch {
                        executeUpdate()
                    }
                },
                delayTicks
            )
    }

    /**
     * cron発火時の処理
     *
     * 1. 更新チェックを1回だけ実行して分類する
     * 2. 更新対象がある場合のみ、実行につき1度だけ更新前バックアップを作成する
     * 3. autoUpdate対象（動的指定かつ非ロック）のみを個別に更新する
     *    - sync:子孫の追従更新は [PluginUpdateService.update] が面倒を見るため、
     *      スケジューラ側では追従処理を一切行わない（二重更新の防止）
     * 4. すべての分類を報告する
     * 5. 実際に更新が起きた場合のみロックファイルを再生成する
     */
    private suspend fun executeUpdate() {
        val prefix = SCHEDULED_PREFIX
        plugin.logger.info("$prefix Checking for plugin updates...")

        val specs = loadVersionSpecs(prefix)
        val (classification, hasCheckErrors) = runCheck(prefix, specs) ?: return

        // 更新前に全分類を報告する（更新に失敗しても状況が分かるようにする）
        reportClassification(prefix, classification, specs, hasCheckErrors)

        // 対象を1件ずつ更新するが、バックアップはcron1回につき1度だけ作成する
        // （個別更新に任せるとplugins/全体のZIPが更新対象数だけ作られてしまう）
        if (classification.autoUpdate.isNotEmpty()) {
            backupManager.createBackup(BackupReason.UPDATE).fold(
                { error -> plugin.logger.warning("$prefix バックアップ作成失敗: ${error.message} - 更新を続行します") },
                { info -> plugin.logger.info("$prefix バックアップ作成完了: ${info.fileName}") }
            )
        }

        var anyUpdated = false
        for (target in classification.autoUpdate) {
            val result =
                updateService.update(
                    PluginName(target.pluginName),
                    force = false,
                    skipIntegrity = false,
                    // 上でまとめて1度バックアップ済みのため、個別更新側では作成しない
                    skipBackup = true
                )
            result.fold(
                { error -> reportUpdateError(prefix, target.pluginName, error) },
                { results ->
                    val updated = reportUpdateResults(prefix, results, specs)
                    anyUpdated = anyUpdated || updated
                }
            )
        }

        if (anyUpdated) {
            // スケジューラはコマンド層を経由しないため、ここで明示的にロックファイルを再生成する
            lockService.regenerateQuietly(plugin.logger)
        } else {
            plugin.logger.info("$prefix No plugins were updated.")
        }
    }

    /**
     * 更新チェックを実行し、mpm.jsonのバージョン指定とロック状態で分類する
     *
     * 起動時チェックとcron実行の双方が同じ分類を通るようにするための共通処理
     *
     * @param prefix ログ出力の接頭辞
     * @param specs mpm.jsonの「プラグイン名 -> バージョン指定文字列」マップ
     * @return 分類結果とチェックエラーの有無のペア。チェック自体に失敗した場合はnull
     */
    private suspend fun runCheck(
        prefix: String,
        specs: Map<String, String>
    ): Pair<UpdateCandidateClassification, Boolean>? =
        infoService.checkAllOutdated().fold(
            { error ->
                plugin.logger.warning("$prefix Update check failed: ${error.message}")
                null
            },
            { checkResult ->
                // チェックに失敗したプラグインを警告表示
                checkResult.errors.forEach { checkError ->
                    plugin.logger.warning(
                        "$prefix Failed to check update for ${checkError.pluginName}: ${checkError.errorMessage}"
                    )
                }

                val needsUpdate = checkResult.outdatedPlugins.filter { it.needsUpdate }
                val classification =
                    UpdateCandidateClassifier.classify(needsUpdate, specs, ::resolveLockState)
                classification to checkResult.errors.isNotEmpty()
            }
        )

    /**
     * 分類結果をログに報告する
     *
     * 更新したもの・しなかったものが理由付きで分かるように、6分類すべてを出力する
     *
     * @param prefix ログ出力の接頭辞
     * @param classification 分類結果
     * @param specs mpm.jsonのバージョン指定マップ（sync:の親名表示に使用）
     * @param hasCheckErrors チェックに失敗したプラグインがあったか
     */
    private fun reportClassification(
        prefix: String,
        classification: UpdateCandidateClassification,
        specs: Map<String, String>,
        hasCheckErrors: Boolean
    ) {
        if (classification.isEmpty && !hasCheckErrors) {
            plugin.logger.info("$prefix All plugins are up to date.")
            return
        }

        logGroup(
            prefix,
            classification.autoUpdate,
            "plugin(s) will be auto-updated (latest / tag:)",
            specs
        )
        logGroup(
            prefix,
            classification.syncFollower,
            "plugin(s) sync to a parent and follow only when the parent updates",
            specs
        )
        logGroup(
            prefix,
            classification.checkOnly,
            "plugin(s) have updates available but are pinned to a fixed version (not updated)",
            specs
        )
        logGroup(
            prefix,
            classification.locked,
            "plugin(s) have updates available but are locked (not updated)",
            specs,
            extraNote = "locked"
        )
        logGroup(
            prefix,
            classification.lockedSync,
            "plugin(s) sync to a parent but are locked (not updated even if the parent updates)",
            specs,
            extraNote = "locked"
        )

        if (classification.unknown.isNotEmpty()) {
            plugin.logger.warning(
                "$prefix WARNING ${classification.unknown.size} plugin(s) could not be checked (metadata error):"
            )
            classification.unknown.forEach { info ->
                plugin.logger.warning("  - ${info.pluginName} (metadata load failed)")
            }
        }
    }

    /**
     * 分類1グループ分をログに出力する
     *
     * @param infos 対象プラグインの更新情報（空の場合は何も出力しない）
     * @param headline 件数に続けて表示する説明文
     * @param extraNote バージョン指定に加えて括弧内に添える補足（ロック中など）
     */
    private fun logGroup(
        prefix: String,
        infos: List<OutdatedInfo>,
        headline: String,
        specs: Map<String, String>,
        extraNote: String? = null
    ) {
        if (infos.isEmpty()) return
        plugin.logger.info("$prefix ${infos.size} $headline:")
        infos.forEach { info ->
            // 「(sync:Parent, locked)」のように指定内容と補足を括弧書きで添える
            val notes = listOfNotNull(specs[info.pluginName], extraNote)
            val suffix = if (notes.isEmpty()) "" else " (${notes.joinToString(", ")})"
            plugin.logger.info(
                "  - ${info.pluginName}: ${info.currentVersion} -> ${info.latestVersion}$suffix"
            )
        }
    }

    /**
     * 1つの親プラグインの更新結果（親 + 連動更新された子）を報告する
     *
     * @param results [PluginUpdateService.update] の戻り値（先頭が親、以降が連動更新された子孫）
     * @param specs mpm.jsonのバージョン指定マップ（sync:の親名解決に使用）
     * @return 実際にバージョンが変わった更新が1件以上あった場合はtrue
     */
    private fun reportUpdateResults(
        prefix: String,
        results: List<UpdateResult>,
        specs: Map<String, String>
    ): Boolean {
        var updated = false
        for (result in results) {
            // sync:指定なら「（親名 に追従）」を文言に付与し、追従による更新だと分かるようにする
            val syncTarget = specs[result.pluginName]?.let { VersionSpecifierParser.extractSyncTarget(it) }
            val suffix = syncTarget?.let { "（$it に追従）" } ?: ""

            when {
                result.success && result.oldVersion != result.newVersion -> {
                    plugin.logger.info(
                        "$prefix ✓ ${result.pluginName} を ${result.oldVersion} → ${result.newVersion} に更新$suffix"
                    )
                    updated = true
                }
                // 現状維持（既に同期済みなど）は報告しない
                result.success -> Unit
                // ロック中などで意図的にスキップした場合は失敗ではないため、警告ではなく情報として報告する
                result.skipped ->
                    plugin.logger.info(
                        "$prefix - ${result.pluginName} は親の更新に追従しませんでした（${result.errorMessage}）"
                    )
                else ->
                    plugin.logger.warning(
                        "$prefix ✗ ${result.pluginName} の更新に失敗しました: ${result.errorMessage}"
                    )
            }
        }
        return updated
    }

    /**
     * 個別更新の失敗をログに出力する
     *
     * 手動更新との競合（UpdateInProgress）は正常動作なのでinfoに留め、
     * いずれの場合も残りの対象の処理は継続する
     */
    private fun reportUpdateError(
        prefix: String,
        pluginName: String,
        error: MpmError
    ) {
        if (error is MpmError.PluginError.UpdateInProgress) {
            plugin.logger.info("$prefix $pluginName: 別の更新処理中のためスキップしました")
        } else {
            plugin.logger.warning("$prefix $pluginName の更新に失敗しました: ${error.message}")
        }
    }

    /**
     * mpm.jsonの「プラグイン名 -> バージョン指定文字列」マップを読み込む
     *
     * mpm.jsonが存在しない、または読めない場合は空マップを返す
     * （その場合すべてがcheckOnlyに分類され、自動更新は行われない安全側の挙動になる）
     *
     * cron経路はinstall経路と違いsync依存の検証を通らないため、ここで健全性を警告する
     *
     * @param prefix ログ出力の接頭辞
     */
    private suspend fun loadVersionSpecs(prefix: String): Map<String, String> {
        val config = projectRepository.find()?.toDto() ?: return emptyMap()
        warnBrokenSyncGraph(prefix, config)
        return config.plugins
    }

    /**
     * 追従更新が成立しないsync依存を警告する
     *
     * mpm.jsonを手編集した場合、install経路の検証を通らないまま
     * 「同期先が存在しない」「同期先がunmanaged」「循環している」状態でcronが回りうる。
     * これらは追従更新が永久に行われないため、原因が分かるように明示的に警告する。
     * （多段sync自体は追従が伝播するようになったため警告しない）
     *
     * @param prefix ログ出力の接頭辞
     * @param config mpm.jsonの内容
     */
    private fun warnBrokenSyncGraph(
        prefix: String,
        config: MpmConfig
    ) {
        for ((child, target) in config.getSyncDependencies()) {
            when (config.plugins[target]) {
                null ->
                    plugin.logger.warning(
                        "$prefix WARNING $child の同期先 '$target' がmpm.jsonに存在しないため、追従更新は行われません"
                    )
                UNMANAGED_SPEC ->
                    plugin.logger.warning(
                        "$prefix WARNING $child の同期先 '$target' は手動管理(unmanaged)のため、追従更新は行われません"
                    )
                else -> Unit
            }
        }

        // 循環しているsync指定は追従の起点が無く、どのプラグインも更新されない
        config.detectCircularDependencies()?.let { cycle ->
            plugin.logger.warning(
                "$prefix WARNING sync指定が循環しているため追従更新は行われません: ${cycle.joinToString(" -> ")}"
            )
        }
    }

    /**
     * メタデータからプラグインのロック状態を解決する
     *
     * メタデータ読み込み失敗はUNKNOWNとして分離し、誤って更新対象にしない
     */
    private fun resolveLockState(pluginName: String): LockState =
        pluginMetadataManager.loadMetadata(pluginName).fold(
            { LockState.UNKNOWN },
            { if (it.mpmInfo.settings.lock == true) LockState.LOCKED else LockState.UNLOCKED }
        )

    companion object {
        // 起動時チェックのログ接頭辞
        private const val STARTUP_PREFIX = "[Startup]"

        // cron実行時のログ接頭辞
        private const val SCHEDULED_PREFIX = "[Scheduled]"

        // 手動管理を表すバージョン指定
        private const val UNMANAGED_SPEC = "unmanaged"
    }
}