/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.di

import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.slf4j.Logger
import party.morino.mpm.Mpm
import party.morino.mpm.api.domain.downloader.model.TargetPlatform
import party.morino.mpm.platform.LoadedPluginRegistry
import party.morino.mpm.platform.MpmEventDispatcher
import party.morino.mpm.platform.PendingJarShutdownHookFactory
import party.morino.mpm.platform.SelfInfo
import party.morino.mpm.platform.paper.PaperCommandPlatform
import party.morino.mpm.platform.paper.PaperEventDispatcher
import party.morino.mpm.platform.paper.PaperLoadedPluginRegistry
import party.morino.mpm.platform.paper.PaperPendingJarShutdownHookFactory
import party.morino.mpm.platform.paper.PaperSelfInfo
import party.morino.mpm.ui.command.platform.CommandPlatform
import java.io.File

/**
 * Paper 固有の依存を登録する Koin モジュール
 *
 * [CoreModule] と組み合わせて使う。共通ロジックが参照するプラットフォームのインターフェースを Paper の実装に結び付ける。
 */
object PaperModule {
    /**
     * Paper 用モジュールを作成する
     *
     * @param plugin mpm のプラグインインスタンス
     * @return Paper 固有の依存を登録した Koin モジュール
     */
    fun create(plugin: Mpm): Module =
        module {
            // プラグインインスタンス
            single<Mpm> { plugin }
            single<JavaPlugin> { plugin }

            // 共通ロジックのログ出力先（Paper のプラグインロガーと同じ出力先・接頭辞になる）
            single<Logger> { plugin.slF4JLogger }

            // プラグインのデータディレクトリ（plugins/mpm）
            single<File>(named(KoinQualifiers.DATA_DIRECTORY)) { plugin.dataFolder }

            // 動作中のプラットフォーム（ダウンロード対象・記述子の優先順位を決める）
            single { TargetPlatform.PAPER }

            // プラットフォーム固有の機能の Paper 実装
            single<SelfInfo> { PaperSelfInfo() }
            single<LoadedPluginRegistry> { PaperLoadedPluginRegistry() }
            single<MpmEventDispatcher> { PaperEventDispatcher() }
            single<PendingJarShutdownHookFactory> { PaperPendingJarShutdownHookFactory() }

            // コマンドの送信者・パーミッションの解釈（ルートは /mpm）
            single<CommandPlatform> { PaperCommandPlatform() }
        }
}