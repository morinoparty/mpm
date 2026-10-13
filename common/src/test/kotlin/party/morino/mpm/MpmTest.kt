/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm

import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import party.morino.mpm.api.application.health.DoctorService
import party.morino.mpm.api.application.lock.LockService
import party.morino.mpm.api.application.plugin.IntegrityVerifier
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.application.plugin.PluginLifecycleService
import party.morino.mpm.api.application.plugin.PluginUpdateService
import party.morino.mpm.api.application.project.ProjectService
import party.morino.mpm.api.application.scheduler.UpdateScheduler
import party.morino.mpm.api.application.search.PluginSearchService
import party.morino.mpm.api.domain.config.ConfigManager
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.config.model.ConfigData
import party.morino.mpm.api.domain.dependency.DependencyAnalyzer
import party.morino.mpm.api.domain.downloader.DownloaderRepository
import party.morino.mpm.api.domain.downloader.model.TargetPlatform
import party.morino.mpm.api.domain.migration.SchemaMigrator
import party.morino.mpm.api.domain.plugin.scan.InstalledJarScanner
import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import party.morino.mpm.api.domain.project.lock.LockRepository
import party.morino.mpm.api.domain.project.repository.ProjectRepository
import party.morino.mpm.api.domain.repository.RepositoryManager
import party.morino.mpm.api.domain.webhook.WebhookEventType
import party.morino.mpm.api.domain.webhook.WebhookNotifier
import party.morino.mpm.application.health.DoctorServiceImpl
import party.morino.mpm.application.lock.LockServiceImpl
import party.morino.mpm.application.plugin.IntegrityVerifierImpl
import party.morino.mpm.application.plugin.PluginInfoServiceImpl
import party.morino.mpm.application.plugin.PluginLifecycleServiceImpl
import party.morino.mpm.application.plugin.PluginUpdateServiceImpl
import party.morino.mpm.application.project.ProjectServiceImpl
import party.morino.mpm.application.project.SelfRegistrar
import party.morino.mpm.application.scheduler.UpdateSchedulerImpl
import party.morino.mpm.application.search.PluginSearchServiceImpl
import party.morino.mpm.infrastructure.dependency.DependencyAnalyzerImpl
import party.morino.mpm.infrastructure.downloader.DownloaderRepositoryImpl
import party.morino.mpm.infrastructure.migration.SchemaMigratorImpl
import party.morino.mpm.infrastructure.persistence.LockRepositoryImpl
import party.morino.mpm.infrastructure.persistence.ProjectRepositoryImpl
import party.morino.mpm.infrastructure.plugin.scan.InstalledJarScannerImpl
import party.morino.mpm.infrastructure.plugin.service.PluginMetadataManagerImpl
import party.morino.mpm.infrastructure.repository.RepositorySourceManagerFactory
import party.morino.mpm.mock.config.PluginDirectoryMock
import party.morino.mpm.mock.platform.AllowingEventDispatcher
import party.morino.mpm.mock.platform.TestSelfInfo
import party.morino.mpm.platform.LoadedPluginRegistry
import party.morino.mpm.platform.MpmEventDispatcher
import party.morino.mpm.platform.PendingJarShutdownHookFactory
import party.morino.mpm.platform.SelfInfo
import party.morino.mpm.utils.TestConfigLoader
import java.io.File

class MpmTest :
    BeforeEachCallback,
    AfterEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        println("beforeEach() executed before " + context.displayName + ".")
        setupKoin()
    }

    override fun afterEach(context: ExtensionContext) {
        stopKoin()
    }

    private fun setupKoin() {
        // 前のテストのKoinが残っていても影響しないよう停止してから始める
        stopKoin()

        // テスト用のモジュールを定義
        // プラットフォーム固有の依存はサーバーを起動せずに済むよう、テスト用の実装を登録する
        val appModule =
            module {
                single<Logger> { LoggerFactory.getLogger("mpm-test") }
                single { TargetPlatform.PAPER }
                single<SelfInfo> { TestSelfInfo() }
                // 稼働中のプラグインが無い状態（どのJARも即時削除できる）を模す
                single<LoadedPluginRegistry> {
                    object : LoadedPluginRegistry {
                        override fun loadedJars(): List<File> = emptyList()

                        override fun enabledJars(): List<File> = emptyList()
                    }
                }
                single<MpmEventDispatcher> { AllowingEventDispatcher() }
                // テストでは JVM のシャットダウンフックを登録しない
                single<PendingJarShutdownHookFactory> {
                    PendingJarShutdownHookFactory { _, _ -> Thread {} }
                }
                single<PluginDirectory> { PluginDirectoryMock() }

                // ConfigManagerの登録
                // インジェクトによるモックではなく、test resources配下のconfig.jsonから読み込む
                // TestConfigLoaderはconfig.local.json > config.jsonの順で解決するので、
                // 開発者はローカルでGitHub tokenなどを設定可能
                single<ConfigManager> {
                    object : ConfigManager {
                        private val config: ConfigData = TestConfigLoader.load()

                        override fun getConfig(): ConfigData = config

                        override suspend fun reload() { /* テストでは動的リロードは不要 */ }
                    }
                }

                // スキーマ移行の登録（本番のDIグラフと構成を揃えるため。テストからは呼び出さない）
                single<SchemaMigrator> { SchemaMigratorImpl() }

                // リポジトリマネージャーの登録
                single<RepositoryManager> {
                    RepositorySourceManagerFactory.create(get(), get())
                }

                // リポジトリの登録
                single<DownloaderRepository> { DownloaderRepositoryImpl() }
                single<ProjectRepository> { ProjectRepositoryImpl() }
                single<LockRepository> { LockRepositoryImpl() }

                // メタデータマネージャーの登録
                single<PluginMetadataManager> { PluginMetadataManagerImpl() }

                // pluginsディレクトリのスキャナーの登録
                single<InstalledJarScanner> { InstalledJarScannerImpl() }

                // 依存関係解析の登録
                single<DependencyAnalyzer> { DependencyAnalyzerImpl() }

                // Webhook通知の登録（テスト用no-op実装）
                single<WebhookNotifier> {
                    object : WebhookNotifier {
                        override fun notify(
                            eventType: WebhookEventType,
                            title: String,
                            description: String,
                            color: Int,
                            fields: List<Pair<String, String>>
                        ) {}

                        override fun isEventEnabled(eventType: WebhookEventType) = false

                        override fun shutdown() {}
                    }
                }

                // 整合性検証の登録
                single<IntegrityVerifier> { IntegrityVerifierImpl() }

                // 新しいApplication Serviceの登録
                single<PluginInfoService> { PluginInfoServiceImpl() }
                single<PluginSearchService> { PluginSearchServiceImpl() }
                single<LockService> { LockServiceImpl() }
                single<DoctorService> { DoctorServiceImpl() }
                single<PluginLifecycleService> { PluginLifecycleServiceImpl() }
                single<PluginUpdateService> { PluginUpdateServiceImpl() }
                single<ProjectService> { ProjectServiceImpl() }
                single { SelfRegistrar() }

                // スケジューラーの登録
                single<UpdateScheduler> { UpdateSchedulerImpl() }
            }

        // テスト用のモジュールでKoinを初期化
        GlobalContext.startKoin {
            modules(appModule)
        }
    }
}