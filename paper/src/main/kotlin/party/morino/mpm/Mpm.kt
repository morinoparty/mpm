/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm

import kotlinx.coroutines.runBlocking
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.context.GlobalContext
import party.morino.mpm.api.MpmAPI
import party.morino.mpm.api.application.dependency.DependencyService
import party.morino.mpm.api.application.job.JobService
import party.morino.mpm.api.application.plugin.DeferredJarDeletion
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.application.plugin.PluginLifecycleService
import party.morino.mpm.api.application.plugin.PluginUpdateService
import party.morino.mpm.api.application.project.ProjectService
import party.morino.mpm.api.application.scheduler.UpdateScheduler
import party.morino.mpm.api.domain.backup.ServerBackupManager
import party.morino.mpm.api.domain.config.ConfigManager
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.downloader.DownloaderRepository
import party.morino.mpm.api.domain.migration.SchemaMigrator
import party.morino.mpm.api.domain.migration.SchemaVersions
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import party.morino.mpm.api.domain.repository.RepositoryManager
import party.morino.mpm.api.domain.webhook.WebhookNotifier
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import party.morino.mpm.di.CoreModule
import party.morino.mpm.di.PaperModule
import party.morino.mpm.event.listener.WebhookEventListener
import party.morino.mpm.infrastructure.mineauth.MineAuthIntegration
import party.morino.mpm.infrastructure.mineauth.MpmApiPermission
import party.morino.mpm.platform.LoadedPluginRegistry
import party.morino.mpm.ui.command.ReloadCommand
import party.morino.mpm.ui.command.manage.control.BackupCommand
import party.morino.mpm.ui.command.manage.control.CacheCommand
import party.morino.mpm.ui.command.manage.control.InitCommand
import party.morino.mpm.ui.command.manage.control.LockCommand
import party.morino.mpm.ui.command.manage.control.PinCommand
import party.morino.mpm.ui.command.manage.info.DependencyCommand
import party.morino.mpm.ui.command.manage.info.DoctorCommand
import party.morino.mpm.ui.command.manage.info.InfoCommand
import party.morino.mpm.ui.command.manage.info.ListCommand
import party.morino.mpm.ui.command.manage.info.OutdatedCommand
import party.morino.mpm.ui.command.manage.info.SearchCommand
import party.morino.mpm.ui.command.manage.info.VerifyCommand
import party.morino.mpm.ui.command.manage.info.VersionsCommand
import party.morino.mpm.ui.command.manage.lifecycle.AddCommand
import party.morino.mpm.ui.command.manage.lifecycle.AdoptCommand
import party.morino.mpm.ui.command.manage.lifecycle.InstallCommand
import party.morino.mpm.ui.command.manage.lifecycle.RemoveCommand
import party.morino.mpm.ui.command.manage.lifecycle.RollbackCommand
import party.morino.mpm.ui.command.manage.lifecycle.UninstallCommand
import party.morino.mpm.ui.command.manage.lifecycle.UpdateCommand
import party.morino.mpm.ui.command.repo.RepositoryCommands
import party.morino.mpm.utils.command.resolver.InstalledPluginParameterType
import party.morino.mpm.utils.command.resolver.RepositoryPluginParameterType
import party.morino.mpm.utils.command.resolver.VersionSpecifierParameterType
import revxrsal.commands.bukkit.BukkitLamp

/**
 * mpmのメインクラス
 * プラグインの起動・終了処理やDIコンテナの設定を担当
 */
open class Mpm :
    JavaPlugin(),
    MpmAPI {
    // コア設定のインスタンスをKoinから遅延初期化
    private val _configManager: ConfigManager by lazy { GlobalContext.get().get() }
    private val _pluginDirectory: PluginDirectory by lazy { GlobalContext.get().get() }
    private val _pluginMetadataManager: PluginMetadataManager by lazy { GlobalContext.get().get() }
    private val _repositoryManager: RepositoryManager by lazy { GlobalContext.get().get() }

    // Application Serviceのインスタンス
    private val _pluginInfoService: PluginInfoService by lazy { GlobalContext.get().get() }
    private val _pluginLifecycleService: PluginLifecycleService by lazy { GlobalContext.get().get() }
    private val _pluginUpdateService: PluginUpdateService by lazy { GlobalContext.get().get() }
    private val _projectService: ProjectService by lazy { GlobalContext.get().get() }
    private val _dependencyService: DependencyService by lazy { GlobalContext.get().get() }
    private val _serverBackupManager: ServerBackupManager by lazy { GlobalContext.get().get() }

    /**
     * プラグイン有効化時の処理
     * DIコンテナとコマンドハンドラーの初期化を行う
     */
    override fun onEnable() {
        // DIコンテナの初期化
        setupKoin()
        runBlocking {
            // 設定ファイルのスキーマ移行を最優先で行う（ConfigManagerがconfig.jsonを読むより前）
            runSchemaMigration()
            _configManager.reload()
        }

        // 前回削除しきれなかった旧JARを片付ける（読み込まれているJARは削除しない）
        GlobalContext.get().get<DeferredJarDeletion>().apply {
            cleanupOnStartup(GlobalContext.get().get<LoadedPluginRegistry>().loadedJars())
            // mpm より後に停止するプラグインの旧JARを、JVM の終了時に削除できるようにする
            installShutdownHook()
        }

        // パーミッション階層の登録（mpm.commandが全子パーミッションを含む）
        registerPermissions()

        // Lampコマンドハンドラーの初期化
        setupCommandHandler()

        // Webhookイベントリスナーの登録
        server.pluginManager.registerEvents(WebhookEventListener(), this)

        // MineAuth HTTP API統合の初期化（MineAuthが存在する場合のみ有効化）
        MineAuthIntegration(this).setup()

        // 自動更新スケジューラーの起動
        GlobalContext.get().get<UpdateScheduler>().start()

        logger.info("mpm has been enabled!")
    }

    /**
     * プラグイン無効化時の処理
     */
    override fun onDisable() {
        // スケジューラーの停止（Koin未初期化時はスキップ）
        GlobalContext.getOrNull()?.get<UpdateScheduler>()?.stop()

        // 実行中の非同期ジョブの停止（Koin停止後のBean参照を避けるため、stopKoinより前に行う）
        GlobalContext.getOrNull()?.getOrNull<JobService>()?.shutdown()

        // Webhookリソースの解放（Koin未初期化時はスキップ）
        GlobalContext.getOrNull()?.get<WebhookNotifier>()?.shutdown()

        // リポジトリソース・ダウンローダーのHTTPクライアントを解放（コネクションリーク防止）
        // Bean未登録でも例外で後続cleanupを止めないようgetOrNullを使用する
        GlobalContext.getOrNull()?.getOrNull<RepositoryManager>()?.shutdown()
        GlobalContext.getOrNull()?.getOrNull<DownloaderRepository>()?.shutdown()

        // 削除を予約していた旧JARのうち、もう使われていないものを削除する（残りはシャットダウンフックが削除する）
        GlobalContext.getOrNull()?.getOrNull<DeferredJarDeletion>()?.deleteScheduled()

        // Koin DIコンテナを停止（リソースリーク防止）
        GlobalContext.stopKoin()

        logger.info("mpm has been disabled!")
    }

    /**
     * 設定ファイル（mpm.json / config.json / metadata 配下の yaml）のスキーマ移行を実行し、結果をログに出力する
     *
     * 移行の失敗はプラグインの起動を止めない。該当ファイルはそのままの内容で扱われる
     */
    private suspend fun runSchemaMigration() {
        val report = GlobalContext.get().get<SchemaMigrator>().migrateAll()

        if (report.migratedCount > 0) {
            logger.info("Migrated ${report.migratedCount} file(s) to schema v${SchemaVersions.CURRENT}.")
        }
        // 新しいmpmで書かれたファイルはダウングレードできないため、警告だけ出してそのまま扱う
        report.futures.forEach {
            logger.warning(
                "${it.fileName} は新しいスキーマ (v${it.foundVersion}) で書かれています。" +
                    "ダウングレードは行わず、そのまま扱います。"
            )
        }
        report.failures.forEach {
            logger.warning("${it.fileName} のスキーマ移行に失敗しました: ${it.reason}")
        }
    }

    /**
     * パーミッション階層の登録
     * mpm.commandが全子パーミッションを含むように設定する（後方互換性）
     *
     * HTTP API のパーミッションは `mpm.api.read` / `mpm.api.write` に分割されており、
     * 従来の `mpm.api` は両方を子に持つ親として登録する。これにより既存の
     * `mpm.api` 付与はそのまま全エンドポイントへのアクセスを維持する。
     */
    private fun registerPermissions() {
        val childPermissions =
            listOf(
                "mpm.command.add",
                "mpm.command.remove",
                "mpm.command.install",
                "mpm.command.uninstall",
                "mpm.command.update",
                "mpm.command.rollback",
                "mpm.command.list",
                "mpm.command.backup",
                "mpm.command.cache",
                "mpm.command.lock",
                "mpm.command.init",
                "mpm.command.reload",
                // MineAuth HTTP API 権限（mpm.command の子として OP に自動付与）
                MpmApiPermission.ROOT
            )
        // 子パーミッションを登録（OP は親経由で全子権限を持つ）
        val children = childPermissions.associateWith { true }
        val parentPermission =
            org.bukkit.permissions.Permission(
                "mpm.command",
                "All mpm commands",
                org.bukkit.permissions.PermissionDefault.OP,
                children
            )
        server.pluginManager.addPermission(parentPermission)

        // HTTP API の read/write を mpm.api の子として登録する。
        // mpm.api 自体のデフォルトは付与しない（OP は mpm.command 経由で継承する）ため、
        // 「mpm.api を持つ = read と write の両方を持つ」という従来の意味が保たれる。
        val apiPermission =
            org.bukkit.permissions.Permission(
                MpmApiPermission.ROOT,
                "All mpm HTTP API endpoints",
                org.bukkit.permissions.PermissionDefault.FALSE,
                mapOf(
                    MpmApiPermission.READ to true,
                    MpmApiPermission.WRITE to true
                )
            )
        server.pluginManager.addPermission(apiPermission)
    }

    /**
     * Koinのセットアップ
     * 依存性注入の設定を行う
     */
    private fun setupKoin() {
        // 既存のKoinコンテキストが残っている場合は停止してから再起動
        GlobalContext.stopKoin()
        GlobalContext.startKoin {
            // 共通のサービス群と、Paper 固有の依存（Logger・データディレクトリ・プラットフォーム実装）を組み合わせる
            modules(CoreModule.create(), PaperModule.create(this@Mpm))
        }
    }

    /**
     * Lampコマンドハンドラーのセットアップ
     * コマンドの登録とParameterTypeの設定を行う
     */
    private fun setupCommandHandler() {
        // BukkitLampの作成
        val lamp =
            BukkitLamp
                .builder(this)
                .parameterTypes { builder ->
                    // カスタムParameterTypeの登録
                    builder.addParameterType(RepositoryPlugin::class.java, RepositoryPluginParameterType())
                    builder.addParameterType(InstalledPlugin::class.java, InstalledPluginParameterType())
                    builder.addParameterType(VersionSpecifier::class.java, VersionSpecifierParameterType())
                }.build()

        // 全コマンドの登録
        lamp.register(AddCommand())
        lamp.register(AdoptCommand())
        lamp.register(BackupCommand())
        lamp.register(CacheCommand())
        lamp.register(DependencyCommand())
        lamp.register(InitCommand())
        lamp.register(InstallCommand())
        lamp.register(ListCommand())
        lamp.register(LockCommand())
        lamp.register(OutdatedCommand())
        lamp.register(RemoveCommand())
        lamp.register(RollbackCommand())
        lamp.register(UninstallCommand())
        lamp.register(PinCommand())
        lamp.register(UpdateCommand())
        lamp.register(InfoCommand())
        lamp.register(SearchCommand())
        lamp.register(DoctorCommand())
        lamp.register(VerifyCommand())
        lamp.register(VersionsCommand())
        lamp.register(RepositoryCommands())
        lamp.register(ReloadCommand())
    }

    // API getters - 式本体で簡潔に
    override fun getConfigManager(): ConfigManager = _configManager

    override fun getPluginDirectory(): PluginDirectory = _pluginDirectory

    override fun getPluginInfoService(): PluginInfoService = _pluginInfoService

    override fun getPluginLifecycleService(): PluginLifecycleService = _pluginLifecycleService

    override fun getPluginUpdateService(): PluginUpdateService = _pluginUpdateService

    override fun getPluginMetadataManager(): PluginMetadataManager = _pluginMetadataManager

    override fun getProjectService(): ProjectService = _projectService

    override fun getRepositoryManager(): RepositoryManager = _repositoryManager

    override fun getDependencyService(): DependencyService = _dependencyService

    override fun getServerBackupManager(): ServerBackupManager = _serverBackupManager
}