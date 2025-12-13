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
import party.morino.mpm.api.core.plugin.PluginInfoManager
import party.morino.mpm.api.model.plugin.InstalledPlugin

/**
 * インストール済みプラグインのパーサー
 * インストール済みのプラグインIDを解析する
 */
class InstalledPluginParser<C> :
    ArgumentParser<C, InstalledPlugin>,
    BlockingSuggestionProvider.Strings<CommandSender>,
    KoinComponent {
    // PluginInfoManagerをKoinから注入
    private val pluginInfoManager: PluginInfoManager by inject()

    /**
     * プラグインIDをパースしてInstalledPluginを返す
     */
    override fun parse(
        commandContext: CommandContext<C & Any>,
        commandInput: CommandInput
    ): ArgumentParseResult<InstalledPlugin> {
        // プラグインIDを読み取る
        val pluginId = commandInput.readString()

        // インストール済みプラグイン一覧を取得（suspendなのでrunBlockingを使用）
        val installedPlugins =
            runBlocking {
                pluginInfoManager.getAllManagedPlugins().map { pluginData ->
                    // PluginDataから名前を取得
                    when (pluginData) {
                        is party.morino.mpm.api.model.plugin.PluginData.BukkitPluginData -> pluginData.name
                        is party.morino.mpm.api.model.plugin.PluginData.PaperPluginData -> pluginData.name
                    }
                }
            }

        // プラグインIDがインストール済みプラグイン一覧に含まれているかチェック
        return if (installedPlugins.contains(pluginId)) {
            // 成功: InstalledPluginを返す
            ArgumentParseResult.success(InstalledPlugin(pluginId))
        } else {
            // 失敗: エラーメッセージを返す
            ArgumentParseResult.failure(
                Throwable("プラグイン '$pluginId' はインストールされていません。")
            )
        }
    }

    /**
     * サジェスト用の文字列一覧を返す
     * インストール済みのプラグイン一覧を返す
     */
    override fun stringSuggestions(
        commandContext: CommandContext<CommandSender?>,
        input: CommandInput
    ): Iterable<String> {
        // インストール済みプラグイン一覧を取得（suspendなのでrunBlockingを使用）
        return runBlocking {
            pluginInfoManager.getAllManagedPlugins().map { pluginData ->
                // PluginDataから名前を取得
                when (pluginData) {
                    is party.morino.mpm.api.model.plugin.PluginData.BukkitPluginData -> pluginData.name
                    is party.morino.mpm.api.model.plugin.PluginData.PaperPluginData -> pluginData.name
                }
            }
        }
    }

    companion object {
        /**
         * InstalledPluginParserのParserDescriptorを返す
         */
        fun installedPluginParser(): ParserDescriptor<CommandSender, InstalledPlugin> =
            ParserDescriptor.of(InstalledPluginParser(), InstalledPlugin::class.java)
    }
}