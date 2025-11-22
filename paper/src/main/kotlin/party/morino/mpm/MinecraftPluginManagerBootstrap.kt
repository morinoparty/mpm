/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.bootstrap.PluginProviderContext
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import org.incendo.cloud.CommandManager
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.kotlin.coroutines.annotations.installCoroutineSupport
import org.incendo.cloud.paper.PaperCommandManager
import party.morino.mpm.ui.commands.InstallCommand
import party.morino.mpm.ui.commands.ListCommand
import party.morino.mpm.utils.CommandSenderMapper

/**
 * MinecraftPluginManagerのブートストラップクラス
 * プラグインの初期化処理を行う
 */
@Suppress("unused")
class MinecraftPluginManagerBootstrap : PluginBootstrap {
    /**
     * プラグインのブートストラップ処理を行うメソッド
     * コマンドマネージャーの設定とコマンドの登録を行う
     * @param context ブートストラップコンテキスト
     */
    override fun bootstrap(context: BootstrapContext) {
        // コマンドマネージャーのインスタンスを作成
        val commandManager: CommandManager<CommandSender> =
            PaperCommandManager
                .builder(CommandSenderMapper())
                .executionCoordinator(ExecutionCoordinator.asyncCoordinator()) // 非同期実行コーディネーターを設定
                .buildBootstrapped(context) // ブートストラップされたコマンドマネージャーを構築

        // アノテーションパーサーのインスタンスを作成
        val annotationParser = AnnotationParser(commandManager, CommandSender::class.java)
        annotationParser.installCoroutineSupport()

        // コマンドの登録
        with(annotationParser) {
            parse(
                InstallCommand(),
                ListCommand()
            )
        }
    }

    /**
     * プラグインのインスタンスを作成するメソッド
     * @param context プラグインプロバイダコンテキスト
     * @return MinecraftPluginManagerプラグインのインスタンス
     */
    override fun createPlugin(context: PluginProviderContext): JavaPlugin {
        return MinecraftPluginManager() // MinecraftPluginManagerプラグインのインスタンスを返す
    }
}