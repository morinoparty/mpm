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
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.brigadier.BrigadierSetting
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.kotlin.coroutines.annotations.installCoroutineSupport
import org.incendo.cloud.paper.PaperCommandManager
import org.incendo.cloud.setting.Configurable
import party.morino.mpm.ui.command.HelpCommand
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
import party.morino.mpm.utils.CommandSenderMapper
import party.morino.mpm.utils.command.parser.InstalledPluginParser
import party.morino.mpm.utils.command.parser.RepositoryPluginParser

/**
 * MinecraftPluginManagerのブートストラップクラス
 * プラグインの初期化処理を行う
 */
@Suppress("unused")
class MpmBootstrap : PluginBootstrap {
    lateinit var commandManager: PaperCommandManager.Bootstrapped<CommandSender>
    private val commands =
        listOf(
            AddCommand(),
            InitCommand(),
            InstallCommand(),
            ListCommand(),
            LockCommand(),
            OutdatedCommand(),
            RemoveCommand(),
            UninstallCommand(),
            UpdateCommand(),
            VersionsCommand(),
            RepositoryCommands(),
                HelpCommand(),
                ReloadCommand(),
        )

    /**
     * プラグインのブートストラップ処理を行うメソッド
     * コマンドマネージャーの設定とコマンドの登録を行う
     * @param context ブートストラップコンテキスト
     */
    override fun bootstrap(context: BootstrapContext) {
        // コマンドマネージャーのインスタンスを作成
        commandManager =
            PaperCommandManager
                .builder(CommandSenderMapper())
                .executionCoordinator(ExecutionCoordinator.simpleCoordinator()) // 非同期実行コーディネーターを設定
                .buildBootstrapped(context) // ブートストラップされたコマンドマネージャーを構築

        val manager = commandManager.brigadierManager()
        val settings: Configurable<BrigadierSetting> = manager.settings()
        manager.setNativeNumberSuggestions(true)
        settings.set(BrigadierSetting.FORCE_EXECUTABLE, true)

        // アノテーションパーサーのインスタンスを作成
        val annotationParser = AnnotationParser(commandManager, CommandSender::class.java)
        annotationParser.installCoroutineSupport()

        commandManager.parserRegistry().registerParser(InstalledPluginParser.installedPluginParser())
        commandManager.parserRegistry().registerParser(RepositoryPluginParser.repositoryPluginParser())

        // コマンドの登録
        with(annotationParser) {
            parse(
                commands
            )
        }
    }

    /**
     * プラグインのインスタンスを作成するメソッド
     * @param context プラグインプロバイダコンテキスト
     * @return mpmプラグインのインスタンス
     */
    override fun createPlugin(context: PluginProviderContext): JavaPlugin {
        return Mpm()
    }
}