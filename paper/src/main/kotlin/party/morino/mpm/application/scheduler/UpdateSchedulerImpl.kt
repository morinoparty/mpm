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
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.application.plugin.PluginUpdateService
import party.morino.mpm.api.application.scheduler.UpdateScheduler
import party.morino.mpm.api.domain.config.ConfigManager
import party.morino.mpm.api.domain.config.model.ScheduleConfig
import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * cron式に基づくプラグイン自動更新スケジューラーの実装
 *
 * nextExecution()で次回実行時刻を正確に計算し、
 * BukkitSchedulerのrunTaskLaterAsynchronouslyで正確にスケジュールする
 */
class UpdateSchedulerImpl : UpdateScheduler, KoinComponent {
    // Koinによる依存性注入
    private val plugin: JavaPlugin by inject()
    private val configManager: ConfigManager by inject()
    private val updateService: PluginUpdateService by inject()
    private val infoService: PluginInfoService by inject()
    private val pluginMetadataManager: PluginMetadataManager by inject()

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
    private val cronParser = CronParser(
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
        val cron = try {
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
        scheduleNext(executionTime, scheduleConfig, currentGeneration)

        val modeLabel = if (scheduleConfig.dryRun) "dry-run" else "auto-update"
        plugin.logger.info("Scheduled $modeLabel started with cron: '${scheduleConfig.cron}'")
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
     * チェック結果をコンソールに出力する（更新は行わない）
     * ロック済みプラグインは別途表示する
     */
    private fun runStartupCheck() {
        scope.launch {
            plugin.logger.info("Checking for plugin updates on startup...")
            infoService.checkAllOutdated().fold(
                { error ->
                    plugin.logger.warning("Startup update check failed: ${error.message}")
                },
                { outdatedList ->
                    // 更新が必要なプラグインをロック状態で分類
                    val needsUpdate = outdatedList.filter { it.needsUpdate }
                    val result = classifyByLockStatus(needsUpdate.map { it.pluginName })

                    val updatableInfos = needsUpdate.filter { it.pluginName in result.updatable }
                    val lockedInfos = needsUpdate.filter { it.pluginName in result.locked }
                    val unknownInfos = needsUpdate.filter { it.pluginName in result.unknown }

                    if (updatableInfos.isEmpty() && lockedInfos.isEmpty() && unknownInfos.isEmpty()) {
                        plugin.logger.info("All plugins are up to date.")
                    } else {
                        if (updatableInfos.isNotEmpty()) {
                            plugin.logger.info("${updatableInfos.size} plugin(s) have updates available:")
                            updatableInfos.forEach { info ->
                                plugin.logger.info(
                                    "  - ${info.pluginName}: ${info.currentVersion} -> ${info.latestVersion}"
                                )
                            }
                        }
                        if (lockedInfos.isNotEmpty()) {
                            plugin.logger.info("${lockedInfos.size} plugin(s) are locked (skipped):")
                            lockedInfos.forEach { info ->
                                plugin.logger.info(
                                    "  - ${info.pluginName}: ${info.currentVersion} -> ${info.latestVersion} (locked)"
                                )
                            }
                        }
                        if (unknownInfos.isNotEmpty()) {
                            plugin.logger.warning(
                                "${unknownInfos.size} plugin(s) could not be checked (metadata error):"
                            )
                            unknownInfos.forEach { info ->
                                plugin.logger.warning("  - ${info.pluginName} (metadata load failed)")
                            }
                        }
                    }
                }
            )
        }
    }

    /**
     * 次回実行時刻を計算し、BukkitSchedulerでスケジュールする
     *
     * 次回予約を先に行い、その後に実処理を実行する
     * 世代管理により、restart後に旧世代のタスクが混入することを防止する
     *
     * @param executionTime cron実行時刻の計算オブジェクト
     * @param config スケジュール設定
     * @param expectedGeneration このスケジュールが属する世代
     */
    private fun scheduleNext(
        executionTime: ExecutionTime,
        config: ScheduleConfig,
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
        schedulerTask = plugin.server.scheduler.runTaskLaterAsynchronously(
            plugin,
            Runnable {
                // 停止フラグと世代を再チェック（stop/restartが呼ばれている可能性）
                if (!running || generation.get() != expectedGeneration) return@Runnable

                // 次回予約を処理の前に行う（長い更新でスロットを飛ばさない）
                scheduleNext(executionTime, config, expectedGeneration)

                scope.launch {
                    if (config.dryRun) {
                        executeDryRun()
                    } else {
                        executeUpdate()
                    }
                }
            },
            delayTicks
        )
    }

    /**
     * dry-runモードの実行: 更新チェックと通知のみ
     *
     * ロック済みプラグインは更新対象から除外して表示する
     */
    private suspend fun executeDryRun() {
        plugin.logger.info("[Scheduled/Dry-run] Checking for plugin updates...")
        infoService.checkAllOutdated().fold(
            { error ->
                plugin.logger.warning("[Scheduled/Dry-run] Update check failed: ${error.message}")
            },
            { outdatedList ->
                val needsUpdate = outdatedList.filter { it.needsUpdate }
                // ロック状態でフィルタリング
                val result = classifyByLockStatus(needsUpdate.map { it.pluginName })

                val updatableInfos = needsUpdate.filter { it.pluginName in result.updatable }
                val lockedInfos = needsUpdate.filter { it.pluginName in result.locked }
                val unknownInfos = needsUpdate.filter { it.pluginName in result.unknown }

                if (updatableInfos.isEmpty() && lockedInfos.isEmpty() && unknownInfos.isEmpty()) {
                    plugin.logger.info("[Scheduled/Dry-run] All plugins are up to date.")
                } else if (updatableInfos.isNotEmpty()) {
                    plugin.logger.info(
                        "[Scheduled/Dry-run] ${updatableInfos.size} plugin(s) would be updated:"
                    )
                    updatableInfos.forEach { info ->
                        plugin.logger.info(
                            "  - ${info.pluginName}: ${info.currentVersion} -> ${info.latestVersion}"
                        )
                    }
                }
                if (lockedInfos.isNotEmpty()) {
                    plugin.logger.info("[Scheduled/Dry-run] ${lockedInfos.size} plugin(s) skipped (locked):")
                    lockedInfos.forEach { info ->
                        plugin.logger.info(
                            "  - ${info.pluginName}: ${info.currentVersion} -> ${info.latestVersion} (locked)"
                        )
                    }
                }
                if (unknownInfos.isNotEmpty()) {
                    plugin.logger.warning(
                        "[Scheduled/Dry-run] ${unknownInfos.size} plugin(s) could not be checked:"
                    )
                    unknownInfos.forEach { info ->
                        plugin.logger.warning("  - ${info.pluginName} (metadata load failed)")
                    }
                }
            }
        )
    }

    /**
     * 通常モードの実行: 実際にプラグインを更新する
     */
    private suspend fun executeUpdate() {
        plugin.logger.info("[Scheduled] Starting automatic plugin update...")
        updateService.update(force = false).fold(
            { error ->
                plugin.logger.warning("[Scheduled] Auto-update failed: ${error.message}")
            },
            { results ->
                val success = results.filter { it.success }
                val failed = results.filter { !it.success }

                if (success.isEmpty() && failed.isEmpty()) {
                    plugin.logger.info("[Scheduled] No plugins needed updating.")
                } else {
                    if (success.isNotEmpty()) {
                        plugin.logger.info("[Scheduled] Updated ${success.size} plugin(s):")
                        success.forEach { result ->
                            plugin.logger.info(
                                "  ✓ ${result.pluginName}: ${result.oldVersion} -> ${result.newVersion}"
                            )
                        }
                    }
                    if (failed.isNotEmpty()) {
                        plugin.logger.warning("[Scheduled] Failed to update ${failed.size} plugin(s):")
                        failed.forEach { result ->
                            plugin.logger.warning("  ✗ ${result.pluginName}: ${result.errorMessage}")
                        }
                    }
                }
            }
        )
    }

    /**
     * プラグインのロック状態分類結果
     *
     * @property locked ロック済みプラグイン名
     * @property updatable 更新可能プラグイン名
     * @property unknown メタデータ読み込み失敗のプラグイン名
     */
    private data class LockClassification(
        val locked: Set<String>,
        val updatable: Set<String>,
        val unknown: Set<String>,
    )

    /**
     * プラグイン名のリストをロック状態で3分類する
     *
     * メタデータ読み込み失敗は「unknown」として分離し、
     * 誤って更新可能と表示されることを防止する
     *
     * @param pluginNames プラグイン名のリスト
     * @return LockClassification（locked, updatable, unknownの3分類）
     */
    private fun classifyByLockStatus(pluginNames: List<String>): LockClassification {
        val locked = mutableSetOf<String>()
        val updatable = mutableSetOf<String>()
        val unknown = mutableSetOf<String>()

        for (name in pluginNames) {
            pluginMetadataManager.loadMetadata(name).fold(
                // メタデータ読み込み失敗: unknown
                { unknown.add(name) },
                // メタデータ読み込み成功: ロック状態で分類
                { metadata ->
                    if (metadata.mpmInfo.settings.lock == true) {
                        locked.add(name)
                    } else {
                        updatable.add(name)
                    }
                }
            )
        }

        return LockClassification(locked, updatable, unknown)
    }
}
