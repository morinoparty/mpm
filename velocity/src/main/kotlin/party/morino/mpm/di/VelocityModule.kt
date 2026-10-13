/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.di

import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.proxy.ProxyServer
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.slf4j.Logger
import party.morino.mpm.api.domain.downloader.model.TargetPlatform
import party.morino.mpm.platform.LoadedPluginRegistry
import party.morino.mpm.platform.MpmEventDispatcher
import party.morino.mpm.platform.PendingJarShutdownHookFactory
import party.morino.mpm.platform.SelfInfo
import party.morino.mpm.platform.velocity.VelocityEventDispatcher
import party.morino.mpm.platform.velocity.VelocityLoadedPluginRegistry
import party.morino.mpm.platform.velocity.VelocityPendingJarShutdownHookFactory
import party.morino.mpm.platform.velocity.VelocitySelfInfo
import party.morino.mpm.velocity.MpmVelocity
import java.io.File
import java.nio.file.Path

/**
 * Velocity 固有の依存を登録する Koin モジュール
 *
 * [CoreModule] と組み合わせて使う。共通ロジックが参照するプラットフォームのインターフェースを Velocity の実装に結び付ける。
 */
object VelocityModule {
    /**
     * Velocity 用モジュールを作成する
     *
     * @param plugin mpm のメインクラスのインスタンス
     * @param proxy プロキシサーバー
     * @param logger Velocity が mpm 用に用意したロガー
     * @param dataDirectory mpm のデータディレクトリ（plugins/mpm）
     * @param container mpm 自身のプラグインコンテナ
     * @return Velocity 固有の依存を登録した Koin モジュール
     */
    fun create(
        plugin: MpmVelocity,
        proxy: ProxyServer,
        logger: Logger,
        dataDirectory: Path,
        container: PluginContainer
    ): Module =
        module {
            // プラグインインスタンスとプロキシ
            single<MpmVelocity> { plugin }
            single<ProxyServer> { proxy }
            single<PluginContainer> { container }

            // 共通ロジックのログ出力先
            single<Logger> { logger }

            // プラグインのデータディレクトリ（plugins/mpm）
            // Velocity が渡すパスは作業ディレクトリからの相対パスのため、plugins ディレクトリ（親）を確実に引けるよう絶対パスにする
            single<File>(named(KoinQualifiers.DATA_DIRECTORY)) { dataDirectory.toAbsolutePath().toFile() }

            // 動作中のプラットフォーム（ダウンロード対象・記述子の優先順位を決める）
            single { TargetPlatform.VELOCITY }

            // プラットフォーム固有の機能の Velocity 実装
            single<SelfInfo> { VelocitySelfInfo() }
            single<LoadedPluginRegistry> { VelocityLoadedPluginRegistry() }
            single<MpmEventDispatcher> { VelocityEventDispatcher() }
            single<PendingJarShutdownHookFactory> { VelocityPendingJarShutdownHookFactory() }
        }
}