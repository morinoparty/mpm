/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.velocity

import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import kotlinx.coroutines.runBlocking
import org.koin.core.context.GlobalContext
import org.slf4j.Logger
import party.morino.mpm.api.application.job.JobService
import party.morino.mpm.api.application.plugin.DeferredJarDeletion
import party.morino.mpm.api.application.scheduler.UpdateScheduler
import party.morino.mpm.api.domain.config.ConfigManager
import party.morino.mpm.api.domain.downloader.DownloaderRepository
import party.morino.mpm.api.domain.migration.SchemaMigrator
import party.morino.mpm.api.domain.migration.SchemaVersions
import party.morino.mpm.api.domain.repository.RepositoryManager
import party.morino.mpm.api.domain.webhook.WebhookNotifier
import party.morino.mpm.di.CoreModule
import party.morino.mpm.di.VelocityModule
import party.morino.mpm.platform.LoadedPluginRegistry
import party.morino.mpm.velocity.event.listener.VelocityWebhookEventListener
import java.nio.file.Files
import java.nio.file.Path

/**
 * Velocity 版 mpm のメインクラス
 *
 * Velocity は Guice でこのクラスを生成し、自動的にイベントリスナーとして登録する。
 * コンストラクタの時点ではまだプラグインとして登録されていないため、初期化はすべて [ProxyInitializeEvent] で行う。
 *
 * @param proxy プロキシサーバー
 * @param logger Velocity が mpm 用に用意したロガー
 * @param dataDirectory mpm のデータディレクトリ（plugins/mpm、Velocity は作成しない）
 * @param container mpm 自身のプラグインコンテナ（コンストラクタでは fromInstance が使えないため注入で受け取る）
 */
class MpmVelocity
    @Inject
    constructor(
        private val proxy: ProxyServer,
        private val logger: Logger,
        @param:DataDirectory private val dataDirectory: Path,
        private val container: PluginContainer
    ) {
        /**
         * プロキシの起動時の処理
         *
         * DIコンテナの初期化、設定ファイルの移行と読み込み、旧JARの片付け、Webhook リスナーと自動更新スケジューラーの起動を行う。
         * プロキシはすべてのハンドラーが終わるまで接続を受け付けないため、ここでの処理は起動完了前に終わる。
         *
         * @param event プロキシの初期化イベント
         */
        @Subscribe
        fun onProxyInitialize(event: ProxyInitializeEvent) {
            // Velocity はデータディレクトリを作成しないため、設定ファイルを読む前に作っておく
            Files.createDirectories(dataDirectory)

            // DIコンテナの初期化
            setupKoin()
            runBlocking {
                // 設定ファイルのスキーマ移行を最優先で行う（ConfigManagerがconfig.jsonを読むより前）
                runSchemaMigration()
                GlobalContext.get().get<ConfigManager>().reload()
            }

            // 前回削除しきれなかった旧JARを片付ける（読み込まれているJARは削除しない）
            GlobalContext.get().get<DeferredJarDeletion>().apply {
                cleanupOnStartup(GlobalContext.get().get<LoadedPluginRegistry>().loadedJars())
                // ProxyShutdownEvent で削除できなかった旧JARを、JVM の終了時に削除できるようにする
                installShutdownHook()
            }

            // Webhookイベントリスナーの登録
            proxy.eventManager.register(this, VelocityWebhookEventListener())

            // 自動更新スケジューラーの起動
            GlobalContext.get().get<UpdateScheduler>().start()

            logger.info("mpm has been enabled!")
        }

        /**
         * プロキシの停止時の処理
         *
         * この時点ではまだ mpm のスレッドプールとログ出力が動いているため、後片付けと削除予約済みのJARの削除をここで行う。
         *
         * @param event プロキシの停止イベント
         */
        @Subscribe
        fun onProxyShutdown(event: ProxyShutdownEvent) {
            // 初期化に失敗していた場合は Koin が無いため、取得できるものだけを片付ける
            val koin = GlobalContext.getOrNull()

            // スケジューラーの停止
            koin?.getOrNull<UpdateScheduler>()?.stop()

            // 実行中の非同期ジョブの停止（Koin停止後のBean参照を避けるため、stopKoinより前に行う）
            koin?.getOrNull<JobService>()?.shutdown()

            // Webhookリソースの解放
            koin?.getOrNull<WebhookNotifier>()?.shutdown()

            // リポジトリソース・ダウンローダーのHTTPクライアントを解放（コネクションリーク防止）
            koin?.getOrNull<RepositoryManager>()?.shutdown()
            koin?.getOrNull<DownloaderRepository>()?.shutdown()

            // 削除を予約していた旧JARを削除する
            // Velocity には同じIDのJARを後から置き換える仕組みが無く、旧JARが残ると次回起動時に新旧どちらかが読み込まれないため、ここで消す
            koin?.getOrNull<DeferredJarDeletion>()?.deleteScheduled()

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
                logger.warn(
                    "${it.fileName} は新しいスキーマ (v${it.foundVersion}) で書かれています。" +
                        "ダウングレードは行わず、そのまま扱います。"
                )
            }
            report.failures.forEach {
                logger.warn("${it.fileName} のスキーマ移行に失敗しました: ${it.reason}")
            }
        }

        /**
         * Koinのセットアップ
         *
         * 共通のサービス群と、Velocity 固有の依存（Logger・データディレクトリ・プラットフォーム実装）を組み合わせる。
         * Koin は JAR 内で別パッケージへ移動（relocate）しているため、同じプロキシ上の他プラグインの Koin とは共有されない。
         */
        private fun setupKoin() {
            // 既存のKoinコンテキストが残っている場合は停止してから再起動
            GlobalContext.stopKoin()
            GlobalContext.startKoin {
                modules(
                    CoreModule.create(),
                    VelocityModule.create(this@MpmVelocity, proxy, logger, dataDirectory, container)
                )
            }
        }
    }