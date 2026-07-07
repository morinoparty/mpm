/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
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
import org.koin.dsl.module
import party.morino.mpm.api.MpmAPI
import party.morino.mpm.api.application.dependency.DependencyService
import party.morino.mpm.api.application.lock.LockService
import party.morino.mpm.api.application.plugin.IntegrityVerifier
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.application.plugin.PluginLifecycleService
import party.morino.mpm.api.application.plugin.PluginUpdateService
import party.morino.mpm.api.application.project.ProjectService
import party.morino.mpm.api.application.scheduler.UpdateScheduler
import party.morino.mpm.api.application.search.PluginSearchService
import party.morino.mpm.api.domain.backup.ServerBackupManager
import party.morino.mpm.api.domain.compatibility.ApiVersionChecker
import party.morino.mpm.api.domain.config.ConfigManager
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.dependency.DependencyAnalyzer
import party.morino.mpm.api.domain.downloader.DownloaderRepository
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import party.morino.mpm.api.domain.project.lock.LockRepository
import party.morino.mpm.api.domain.project.repository.ProjectRepository
import party.morino.mpm.api.domain.repository.RepositoryManager
import party.morino.mpm.api.domain.webhook.WebhookNotifier
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import party.morino.mpm.application.dependency.DependencyServiceImpl
import party.morino.mpm.application.lock.LockServiceImpl
import party.morino.mpm.application.plugin.IntegrityVerifierImpl
import party.morino.mpm.application.plugin.PluginInfoServiceImpl
import party.morino.mpm.application.plugin.PluginInstallValidator
import party.morino.mpm.application.plugin.PluginLifecycleServiceImpl
import party.morino.mpm.application.plugin.PluginUpdateServiceImpl
import party.morino.mpm.application.project.ProjectServiceImpl
import party.morino.mpm.application.scheduler.UpdateSchedulerImpl
import party.morino.mpm.application.search.PluginSearchServiceImpl
import party.morino.mpm.event.listener.WebhookEventListener
import party.morino.mpm.infrastructure.backup.ServerBackupManagerImpl
import party.morino.mpm.infrastructure.compatibility.ApiVersionCheckerImpl
import party.morino.mpm.infrastructure.config.ConfigManagerImpl
import party.morino.mpm.infrastructure.config.PluginDirectoryImpl
import party.morino.mpm.infrastructure.dependency.DependencyAnalyzerImpl
import party.morino.mpm.infrastructure.downloader.DownloaderRepositoryImpl
import party.morino.mpm.infrastructure.mineauth.MineAuthIntegration
import party.morino.mpm.infrastructure.persistence.LockRepositoryImpl
import party.morino.mpm.infrastructure.persistence.ProjectRepositoryImpl
import party.morino.mpm.infrastructure.plugin.service.PluginMetadataManagerImpl
import party.morino.mpm.infrastructure.repository.RepositorySourceManagerFactory
import party.morino.mpm.infrastructure.webhook.DiscordWebhookNotifier
import party.morino.mpm.ui.command.ReloadCommand
import party.morino.mpm.ui.command.manage.control.BackupCommand
import party.morino.mpm.ui.command.manage.control.InitCommand
import party.morino.mpm.ui.command.manage.control.LockCommand
import party.morino.mpm.ui.command.manage.control.PinCommand
import party.morino.mpm.ui.command.manage.info.DependencyCommand
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
            _configManager.reload()
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

        // Webhookリソースの解放（Koin未初期化時はスキップ）
        GlobalContext.getOrNull()?.get<WebhookNotifier>()?.shutdown()

        // リポジトリソース・ダウンローダーのHTTPクライアントを解放（コネクションリーク防止）
        // Bean未登録でも例外で後続cleanupを止めないようgetOrNullを使用する
        GlobalContext.getOrNull()?.getOrNull<RepositoryManager>()?.shutdown()
        GlobalContext.getOrNull()?.getOrNull<DownloaderRepository>()?.shutdown()

        // Koin DIコンテナを停止（リソースリーク防止）
        GlobalContext.stopKoin()

        logger.info("mpm has been disabled!")
    }

    /**
     * パーミッション階層の登録
     * mpm.commandが全子パーミッションを含むように設定する（後方互換性）
     */
    private fun registerPermissions() {
        val childPermissions =
            listOf(
                "mpm.command.add",
                "mpm.command.remove",
                "mpm.command.install",
                "mpm.command.uninstall",
                "mpm.command.update",
                "mpm.command.list",
                "mpm.command.backup",
                "mpm.command.lock",
                "mpm.command.init",
                "mpm.command.reload",
                // MineAuth HTTP API 権限（mpm.command の子として OP に自動付与）
                "mpm.api"
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
    }

    /**
     * Koinのセットアップ
     * 依存性注入の設定を行う
     */
    private fun setupKoin() {
        // モジュールの定義
        val appModule =
            module {
                // プラグインインスタンス
                single<Mpm> { this@Mpm }
                single<JavaPlugin> { this@Mpm }

                // 設定の登録（依存性はKoinのinjectによって自動注入される）
                single<PluginDirectory> { PluginDirectoryImpl() }
                single<ConfigManager> { ConfigManagerImpl() }

                // リポジトリマネージャーの登録（ファクトリーを使用）
                single<RepositoryManager> {
                    RepositorySourceManagerFactory.create(get(), get())
                }

                // リポジトリの登録（依存性はKoinのinjectによって自動注入される）
                single<DownloaderRepository> { DownloaderRepositoryImpl() }
                single<ProjectRepository> { ProjectRepositoryImpl() }
                single<LockRepository> { LockRepositoryImpl() }

                // メタデータマネージャーの登録（依存性はKoinのinjectによって自動注入される）
                single<PluginMetadataManager> { PluginMetadataManagerImpl() }

                // バックアップ管理の登録
                single<ServerBackupManager> { ServerBackupManagerImpl() }

                // APIバージョン互換性チェッカーの登録
                single<ApiVersionChecker> { ApiVersionCheckerImpl() }

                // 依存関係解析の登録
                single<DependencyAnalyzer> { DependencyAnalyzerImpl() }
                single<DependencyService> { DependencyServiceImpl() }

                // Webhook通知の登録
                single<WebhookNotifier> { DiscordWebhookNotifier() }

                // Application Serviceの登録
                single<PluginInfoService> { PluginInfoServiceImpl() }
                // リポジトリ横断検索
                single<PluginSearchService> { PluginSearchServiceImpl() }
                // ロックファイル（mpm-lock.yaml）管理
                single<LockService> { LockServiceImpl() }
                // インストール前検証（APIバージョン互換性・依存関係）の共通ロジック
                // PluginLifecycleServiceImplとPluginUpdateServiceImplの両方から利用される
                single { PluginInstallValidator() }
                // ダウンロードしたJARのハッシュ整合性検証
                single<IntegrityVerifier> { IntegrityVerifierImpl() }
                single<PluginLifecycleService> { PluginLifecycleServiceImpl() }
                single<PluginUpdateService> { PluginUpdateServiceImpl() }
                single<ProjectService> { ProjectServiceImpl() }

                // スケジューラーの登録
                single<UpdateScheduler> { UpdateSchedulerImpl() }
            }

        // 既存のKoinコンテキストが残っている場合は停止してから再起動
        GlobalContext.stopKoin()
        GlobalContext.startKoin {
            modules(appModule)
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
        lamp.register(DependencyCommand())
        lamp.register(InitCommand())
        lamp.register(InstallCommand())
        lamp.register(ListCommand())
        lamp.register(LockCommand())
        lamp.register(OutdatedCommand())
        lamp.register(RemoveCommand())
        lamp.register(UninstallCommand())
        lamp.register(PinCommand())
        lamp.register(UpdateCommand())
        lamp.register(InfoCommand())
        lamp.register(SearchCommand())
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