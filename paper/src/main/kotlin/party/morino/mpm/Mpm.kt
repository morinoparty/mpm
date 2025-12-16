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
import party.morino.mpm.api.config.PluginDirectory
import party.morino.mpm.api.core.config.ConfigManager
import party.morino.mpm.api.core.plugin.DownloaderRepository
import party.morino.mpm.api.core.plugin.PluginInfoManager
import party.morino.mpm.api.core.plugin.PluginLifecycleManager
import party.morino.mpm.api.core.plugin.PluginMetadataManager
import party.morino.mpm.api.core.plugin.PluginRepository
import party.morino.mpm.api.core.plugin.PluginUpdateManager
import party.morino.mpm.api.core.plugin.ProjectManager
import party.morino.mpm.api.core.repository.RepositoryManager
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import party.morino.mpm.config.PluginDirectoryImpl
import party.morino.mpm.core.config.ConfigManagerImpl
import party.morino.mpm.core.plugin.PluginInfoManagerImpl
import party.morino.mpm.core.plugin.PluginLifecycleManagerImpl
import party.morino.mpm.core.plugin.PluginUpdateManagerImpl
import party.morino.mpm.core.plugin.ProjectManagerImpl
import party.morino.mpm.core.plugin.infrastructure.DownloaderRepositoryImpl
import party.morino.mpm.core.plugin.infrastructure.PluginMetadataManagerImpl
import party.morino.mpm.core.plugin.infrastructure.PluginRepositoryImpl
import party.morino.mpm.core.repository.RepositorySourceManagerFactory
import party.morino.mpm.ui.command.ReloadCommand
import party.morino.mpm.ui.command.manage.AddCommand
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
import revxrsal.commands.bukkit.BukkitLamp

/**
 * mpmのメインクラス
 * プラグインの起動・終了処理やDIコンテナの設定を担当
 */
open class Mpm :
    JavaPlugin(),
    MpmAPI {
    // 各マネージャーのインスタンスをKoinから遅延初期化
    private val _configManager: ConfigManager by lazy { GlobalContext.get().get() }
    private val _pluginDirectory: PluginDirectory by lazy { GlobalContext.get().get() }
    private val _pluginInfoManager: PluginInfoManager by lazy { GlobalContext.get().get() }
    private val _pluginLifecycleManager: PluginLifecycleManager by lazy { GlobalContext.get().get() }
    private val _pluginUpdateManager: PluginUpdateManager by lazy { GlobalContext.get().get() }
    private val _pluginMetadataManager: PluginMetadataManager by lazy { GlobalContext.get().get() }
    private val _projectManager: ProjectManager by lazy { GlobalContext.get().get() }
    private val _repositoryManager: RepositoryManager by lazy { GlobalContext.get().get() }

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

                // メタデータマネージャーの登録（依存性はKoinのinjectによって自動注入される）
                single<PluginMetadataManager> { PluginMetadataManagerImpl() }

                // ドメイン単位のManagerの登録（Facadeパターンで関連UseCaseをまとめる）
                single<PluginLifecycleManager> { PluginLifecycleManagerImpl() }
                single<PluginInfoManager> { PluginInfoManagerImpl() }
                single<PluginUpdateManager> { PluginUpdateManagerImpl() }
                single<ProjectManager> { ProjectManagerImpl() }
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
                }.build()

        // 全コマンドの登録
        lamp.register(AddCommand())
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

    override fun getPluginInfoManager(): PluginInfoManager = _pluginInfoManager

    override fun getPluginLifecycleManager(): PluginLifecycleManager = _pluginLifecycleManager

    override fun getPluginUpdateManager(): PluginUpdateManager = _pluginUpdateManager

    override fun getPluginMetadataManager(): PluginMetadataManager = _pluginMetadataManager

    override fun getProjectManager(): ProjectManager = _projectManager

    override fun getRepositoryManager(): RepositoryManager = _repositoryManager
}