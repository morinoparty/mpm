/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.di

import org.koin.core.module.Module
import org.koin.dsl.binds
import org.koin.dsl.module
import party.morino.mpm.api.application.dependency.DependencyService
import party.morino.mpm.api.application.health.DoctorService
import party.morino.mpm.api.application.job.JobService
import party.morino.mpm.api.application.lock.LockService
import party.morino.mpm.api.application.plugin.DeferredJarDeletion
import party.morino.mpm.api.application.plugin.IntegrityVerifier
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.application.plugin.PluginJarFileService
import party.morino.mpm.api.application.plugin.PluginLifecycleService
import party.morino.mpm.api.application.plugin.PluginUpdateService
import party.morino.mpm.api.application.project.ProjectService
import party.morino.mpm.api.application.scheduler.OutdatedNotificationLedger
import party.morino.mpm.api.application.scheduler.UpdateScheduler
import party.morino.mpm.api.application.search.PluginSearchService
import party.morino.mpm.api.domain.backup.ServerBackupManager
import party.morino.mpm.api.domain.cache.CacheManager
import party.morino.mpm.api.domain.cache.HttpMetadataCache
import party.morino.mpm.api.domain.config.ConfigManager
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.dependency.DependencyAnalyzer
import party.morino.mpm.api.domain.downloader.DownloaderRepository
import party.morino.mpm.api.domain.migration.SchemaMigrator
import party.morino.mpm.api.domain.plugin.scan.InstalledJarScanner
import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import party.morino.mpm.api.domain.project.lock.LockRepository
import party.morino.mpm.api.domain.project.repository.ProjectRepository
import party.morino.mpm.api.domain.repository.RepositoryManager
import party.morino.mpm.api.domain.webhook.WebhookNotifier
import party.morino.mpm.application.dependency.DependencyServiceImpl
import party.morino.mpm.application.health.DoctorServiceImpl
import party.morino.mpm.application.job.JobServiceImpl
import party.morino.mpm.application.lock.LockServiceImpl
import party.morino.mpm.application.plugin.IntegrityVerifierImpl
import party.morino.mpm.application.plugin.PluginInfoServiceImpl
import party.morino.mpm.application.plugin.PluginInstallValidator
import party.morino.mpm.application.plugin.PluginLifecycleServiceImpl
import party.morino.mpm.application.plugin.PluginUpdateServiceImpl
import party.morino.mpm.application.plugin.file.PluginJarFileServiceImpl
import party.morino.mpm.application.project.ProjectServiceImpl
import party.morino.mpm.application.project.SelfRegistrar
import party.morino.mpm.application.scheduler.UpdateSchedulerImpl
import party.morino.mpm.application.search.PluginSearchServiceImpl
import party.morino.mpm.infrastructure.backup.ServerBackupManagerImpl
import party.morino.mpm.infrastructure.cache.CacheManagerImpl
import party.morino.mpm.infrastructure.cache.HttpMetadataCacheImpl
import party.morino.mpm.infrastructure.config.ConfigLoadDiagnostics
import party.morino.mpm.infrastructure.config.ConfigManagerImpl
import party.morino.mpm.infrastructure.config.PluginDirectoryImpl
import party.morino.mpm.infrastructure.dependency.DependencyAnalyzerImpl
import party.morino.mpm.infrastructure.downloader.DownloaderRepositoryImpl
import party.morino.mpm.infrastructure.migration.SchemaMigratorImpl
import party.morino.mpm.infrastructure.persistence.LockRepositoryImpl
import party.morino.mpm.infrastructure.persistence.ProjectRepositoryImpl
import party.morino.mpm.infrastructure.plugin.retire.DeferredJarDeletionImpl
import party.morino.mpm.infrastructure.plugin.scan.InstalledJarScannerImpl
import party.morino.mpm.infrastructure.plugin.service.PluginMetadataManagerImpl
import party.morino.mpm.infrastructure.repository.RepositorySourceManagerFactory
import party.morino.mpm.infrastructure.scheduler.OutdatedNotificationLedgerImpl
import party.morino.mpm.infrastructure.webhook.DiscordWebhookNotifier

/**
 * Paper / Velocity で共通の Koin モジュール
 *
 * プラットフォーム非依存のサービス・リポジトリ・インフラを登録する。
 * プラットフォーム固有の依存（Logger、データディレクトリ、[party.morino.mpm.platform] 配下のインターフェースの実装、
 * TargetPlatform）は、各プラットフォームのモジュールが登録する。
 */
object CoreModule {
    /**
     * 共通モジュールを作成する
     *
     * @return 共通サービスを登録した Koin モジュール
     */
    fun create(): Module =
        module {
            // 設定の登録（依存性はKoinのinjectによって自動注入される）
            single<PluginDirectory> { PluginDirectoryImpl() }
            // 読み込み失敗の診断情報（ConfigLoadDiagnostics）も同じインスタンスから引けるようにする
            single { ConfigManagerImpl() } binds arrayOf(ConfigManager::class, ConfigLoadDiagnostics::class)

            // スキーマ移行の登録（他サービスが設定ファイルを読むより前に一度だけ実行される）
            single<SchemaMigrator> { SchemaMigratorImpl() }

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

            // plugins ディレクトリのライブスキャン（unmanaged 判定・init で共用）
            single<InstalledJarScanner> { InstalledJarScannerImpl() }

            // 即時削除できない旧JAR（mpm 自身の更新など）の削除予約
            single<DeferredJarDeletion> { DeferredJarDeletionImpl() }

            // HTTPメタデータキャッシュとキャッシュ管理（mpm cache コマンドから利用）
            single<HttpMetadataCache> { HttpMetadataCacheImpl() }
            single<CacheManager> { CacheManagerImpl() }

            // バックアップ管理の登録
            single<ServerBackupManager> { ServerBackupManagerImpl() }

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
            // サーバー健全性診断（mpm doctor）
            single<DoctorService> { DoctorServiceImpl() }
            // インストール前検証（依存関係）の共通ロジック
            // PluginLifecycleServiceImplとPluginUpdateServiceImplの両方から利用される
            single { PluginInstallValidator() }
            // ダウンロードしたJARのハッシュ整合性検証
            single<IntegrityVerifier> { IntegrityVerifierImpl() }
            single<PluginLifecycleService> { PluginLifecycleServiceImpl() }
            single<PluginUpdateService> { PluginUpdateServiceImpl() }
            // plugins/ 直下のJARを管理情報に触れず直接削除する（自己更新で残った旧JARの片付けなど）
            single<PluginJarFileService> { PluginJarFileServiceImpl() }
            single<ProjectService> { ProjectServiceImpl() }
            single { SelfRegistrar() }

            // スケジューラーの登録
            single<UpdateScheduler> { UpdateSchedulerImpl() }
            // outdated 通知の通知済み台帳（定期チェックだけが書く）
            single<OutdatedNotificationLedger> { OutdatedNotificationLedgerImpl() }

            // 非同期ジョブ（HTTP APIの長時間処理）の登録
            single<JobService> { JobServiceImpl() }
        }
}