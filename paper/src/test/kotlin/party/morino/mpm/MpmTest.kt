/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm

import be.seeseemelk.mockbukkit.MockBukkit
import be.seeseemelk.mockbukkit.ServerMock
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.koin.dsl.module
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
import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import party.morino.mpm.api.domain.project.repository.ProjectRepository
import party.morino.mpm.api.domain.repository.RepositoryManager
import party.morino.mpm.api.domain.webhook.WebhookEventType
import party.morino.mpm.api.domain.webhook.WebhookNotifier
import party.morino.mpm.application.plugin.IntegrityVerifierImpl
import party.morino.mpm.application.plugin.PluginInfoServiceImpl
import party.morino.mpm.application.plugin.PluginLifecycleServiceImpl
import party.morino.mpm.application.plugin.PluginUpdateServiceImpl
import party.morino.mpm.application.project.ProjectServiceImpl
import party.morino.mpm.application.scheduler.UpdateSchedulerImpl
import party.morino.mpm.application.search.PluginSearchServiceImpl
import party.morino.mpm.infrastructure.dependency.DependencyAnalyzerImpl
import party.morino.mpm.infrastructure.downloader.DownloaderRepositoryImpl
import party.morino.mpm.infrastructure.persistence.ProjectRepositoryImpl
import party.morino.mpm.infrastructure.plugin.service.PluginMetadataManagerImpl
import party.morino.mpm.infrastructure.repository.RepositorySourceManagerFactory
import party.morino.mpm.mock.config.PluginDirectoryMock
import party.morino.mpm.utils.TestConfigLoader

class MpmTest :
    BeforeEachCallback,
    AfterEachCallback {
    private lateinit var server: ServerMock
    private lateinit var plugin: Mpm

    override fun beforeEach(context: ExtensionContext) {
        println("beforeEach() executed before " + context.displayName + ".")
        server = MockBukkit.mock()
        setupKoin()
    }

    override fun afterEach(context: ExtensionContext) {
        MockBukkit.unmock()
        stopKoin()
    }

    private fun setupKoin() {
        // プラグインをロード（この時点でonEnable()が呼ばれてKoinが初期化される）
        plugin = MockBukkit.load(Mpm::class.java)

        // プラグイン側で初期化されたKoinを停止
        stopKoin()

        // テスト用のモジュールを定義
        val appModule =
            module {
                single<Mpm> { plugin }
                single<JavaPlugin> { plugin }
                single<ServerMock> { server }
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

                // リポジトリマネージャーの登録
                single<RepositoryManager> {
                    RepositorySourceManagerFactory.create(get(), get())
                }

                // リポジトリの登録
                single<DownloaderRepository> { DownloaderRepositoryImpl() }
                single<ProjectRepository> { ProjectRepositoryImpl() }

                // メタデータマネージャーの登録
                single<PluginMetadataManager> { PluginMetadataManagerImpl() }

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
                single<PluginLifecycleService> { PluginLifecycleServiceImpl() }
                single<PluginUpdateService> { PluginUpdateServiceImpl() }
                single<ProjectService> { ProjectServiceImpl() }

                // スケジューラーの登録
                single<UpdateScheduler> { UpdateSchedulerImpl() }
            }

        // テスト用のモジュールでKoinを初期化
        GlobalContext.startKoin {
            modules(appModule)
        }
    }
}