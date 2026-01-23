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
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.application.plugin.PluginLifecycleService
import party.morino.mpm.api.application.plugin.PluginUpdateService
import party.morino.mpm.api.application.project.ProjectService
import party.morino.mpm.api.domain.backup.ServerBackupManager
import party.morino.mpm.api.domain.config.ConfigManager
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.dependency.DependencyAnalyzer
import party.morino.mpm.api.domain.downloader.DownloaderRepository
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.domain.plugin.repository.PluginRepository
import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import party.morino.mpm.api.domain.project.repository.ProjectRepository
import party.morino.mpm.api.domain.repository.RepositoryManager
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import party.morino.mpm.application.dependency.DependencyServiceImpl
import party.morino.mpm.application.plugin.PluginInfoServiceImpl
import party.morino.mpm.application.plugin.PluginLifecycleServiceImpl
import party.morino.mpm.application.plugin.PluginUpdateServiceImpl
import party.morino.mpm.application.project.ProjectServiceImpl
import party.morino.mpm.infrastructure.config.PluginDirectoryImpl
import party.morino.mpm.infrastructure.dependency.DependencyAnalyzerImpl
import party.morino.mpm.infrastructure.plugin.service.PluginMetadataManagerImpl
import party.morino.mpm.infrastructure.backup.ServerBackupManagerImpl
import party.morino.mpm.infrastructure.config.ConfigManagerImpl
import party.morino.mpm.infrastructure.downloader.DownloaderRepositoryImpl
import party.morino.mpm.infrastructure.persistence.PluginRepositoryImpl
import party.morino.mpm.infrastructure.persistence.ProjectRepositoryImpl
import party.morino.mpm.infrastructure.repository.RepositorySourceManagerFactory
import party.morino.mpm.ui.command.ReloadCommand
import party.morino.mpm.ui.command.manage.AddCommand
import party.morino.mpm.ui.command.manage.BackupCommand
import party.morino.mpm.ui.command.manage.DependencyCommand
import party.morino.mpm.ui.command.manage.InitCommand
import party.morino.mpm.ui.command.manage.InstallCommand
import party.morino.mpm.ui.command.manage.ListCommand
import party.morino.mpm.ui.command.manage.LockCommand
import party.morino.mpm.ui.command.manage.OutdatedCommand
import party.morino.mpm.ui.command.manage.RemoveCommand
import party.morino.mpm.ui.command.manage.UninstallCommand
import party.morino.mpm.ui.command.manage.UpdateCommand
import party.morino.mpm.ui.command.manage.VersionsCommand
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

        // Lampコマンドハンドラーの初期化
        setupCommandHandler()

        logger.info("mpm has been enabled!")
    }

    /**
     * プラグイン無効化時の処理
     */
    override fun onDisable() {
        logger.info("mpm has been disabled!")
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
                single<PluginRepository> { PluginRepositoryImpl() }
                single<ProjectRepository> { ProjectRepositoryImpl() }

                // メタデータマネージャーの登録（依存性はKoinのinjectによって自動注入される）
                single<PluginMetadataManager> { PluginMetadataManagerImpl() }

                // バックアップ管理の登録
                single<ServerBackupManager> { ServerBackupManagerImpl() }

                // 依存関係解析の登録
                single<DependencyAnalyzer> { DependencyAnalyzerImpl() }
                single<DependencyService> { DependencyServiceImpl() }

                // Application Serviceの登録
                single<PluginInfoService> { PluginInfoServiceImpl() }
                single<PluginLifecycleService> { PluginLifecycleServiceImpl() }
                single<PluginUpdateService> { PluginUpdateServiceImpl() }
                single<ProjectService> { ProjectServiceImpl() }
            }

        // Koinの開始（すでに開始されている場合は何もしない）
        GlobalContext.getOrNull() ?: GlobalContext.startKoin {
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
        lamp.register(BackupCommand())
        lamp.register(DependencyCommand())
        lamp.register(InitCommand())
        lamp.register(InstallCommand())
        lamp.register(ListCommand())
        lamp.register(LockCommand())
        lamp.register(OutdatedCommand())
        lamp.register(RemoveCommand())
        lamp.register(UninstallCommand())
        lamp.register(UpdateCommand())
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
}