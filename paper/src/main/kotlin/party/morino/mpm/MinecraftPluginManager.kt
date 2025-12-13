/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm

import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import party.morino.mpm.api.config.PluginDirectory
import party.morino.mpm.api.core.plugin.DownloaderRepository
import party.morino.mpm.api.core.plugin.PluginInfoManager
import party.morino.mpm.api.core.plugin.PluginLifecycleManager
import party.morino.mpm.api.core.plugin.PluginMetadataManager
import party.morino.mpm.api.core.plugin.PluginRepository
import party.morino.mpm.api.core.plugin.PluginUpdateManager
import party.morino.mpm.api.core.plugin.ProjectManager
import party.morino.mpm.api.core.repository.PluginRepositorySourceManager
import party.morino.mpm.config.PluginDirectoryImpl
import party.morino.mpm.core.plugin.PluginInfoManagerImpl
import party.morino.mpm.core.plugin.PluginLifecycleManagerImpl
import party.morino.mpm.core.plugin.PluginUpdateManagerImpl
import party.morino.mpm.core.plugin.ProjectManagerImpl
import party.morino.mpm.core.plugin.infrastructure.DownloaderRepositoryImpl
import party.morino.mpm.core.plugin.infrastructure.PluginMetadataManagerImpl
import party.morino.mpm.core.plugin.infrastructure.PluginRepositoryImpl
import party.morino.mpm.core.repository.RepositorySourceManagerFactory

/**
 * MinecraftPluginManagerのメインクラス
 * プラグインの起動・終了処理やDIコンテナの設定を担当
 */
open class MinecraftPluginManager : JavaPlugin() {
    /**
     * プラグイン有効化時の処理
     * DIコンテナの初期化を行う
     */
    override fun onEnable() {
        // DIコンテナの初期化
        setupKoin()
        logger.info("MinecraftPluginManager has been enabled!")
    }

    /**
     * プラグイン無効化時の処理
     */
    override fun onDisable() {
        logger.info("MinecraftPluginManager has been disabled!")
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
                single<MinecraftPluginManager> { this@MinecraftPluginManager }
                single<JavaPlugin> { this@MinecraftPluginManager }

                // 設定の登録（依存性はKoinのinjectによって自動注入される）
                single<PluginDirectory> { PluginDirectoryImpl() }

                // リポジトリソースマネージャーの登録（ファクトリーを使用）
                single<PluginRepositorySourceManager> {
                    RepositorySourceManagerFactory.create(get())
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
}