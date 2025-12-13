/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils.command.parser

import kotlinx.coroutines.runBlocking
import org.bukkit.command.CommandSender
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.context.CommandInput
import org.incendo.cloud.parser.ArgumentParseResult
import org.incendo.cloud.parser.ArgumentParser
import org.incendo.cloud.parser.ParserDescriptor
import org.incendo.cloud.suggestion.BlockingSuggestionProvider
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.core.repository.PluginRepositorySourceManager
import party.morino.mpm.api.model.plugin.RepositoryPlugin

/**
 * リポジトリプラグインのパーサー
 * リポジトリから取得可能なプラグインIDを解析する
 */
class RepositoryPluginParser<C> :
    ArgumentParser<C, RepositoryPlugin>,
    BlockingSuggestionProvider.Strings<CommandSender>,
    KoinComponent {
    // PluginRepositorySourceManagerをKoinから注入
    private val pluginRepositorySourceManager: PluginRepositorySourceManager by inject()

    /**
     * プラグインIDをパースしてRepositoryPluginを返す
     */
    override fun parse(
        commandContext: CommandContext<C & Any>,
        commandInput: CommandInput
    ): ArgumentParseResult<RepositoryPlugin> {
        // プラグインIDを読み取る
        val pluginId = commandInput.readString()

        // リポジトリから取得可能なプラグイン一覧を取得（suspendなのでrunBlockingを使用）
        val availablePlugins =
            runBlocking {
                pluginRepositorySourceManager.getAvailablePlugins()
            }

        // プラグインIDが利用可能なプラグイン一覧に含まれているかチェック
        return if (availablePlugins.contains(pluginId)) {
            // 成功: RepositoryPluginを返す
            ArgumentParseResult.success(RepositoryPlugin(pluginId))
        } else {
            // 失敗: エラーメッセージを返す
            ArgumentParseResult.failure(
                Throwable("プラグイン '$pluginId' はリポジトリに見つかりませんでした。")
            )
        }
    }

    /**
     * サジェスト用の文字列一覧を返す
     * リポジトリから取得可能なプラグイン一覧を返す
     */
    override fun stringSuggestions(
        commandContext: CommandContext<CommandSender?>,
        input: CommandInput
    ): Iterable<String> {
        // リポジトリから取得可能なプラグイン一覧を返す（suspendなのでrunBlockingを使用）
        return runBlocking {
            pluginRepositorySourceManager.getAvailablePlugins()
        }
    }

    companion object {
        /**
         * RepositoryPluginParserのParserDescriptorを返す
         */
        fun repositoryPluginParser(): ParserDescriptor<CommandSender, RepositoryPlugin> =
            ParserDescriptor.of(RepositoryPluginParser(), RepositoryPlugin::class.java)
    }
}