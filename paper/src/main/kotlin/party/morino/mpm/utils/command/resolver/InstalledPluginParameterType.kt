/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils.command.resolver

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.core.plugin.PluginInfoManager
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.api.model.plugin.PluginData
import revxrsal.commands.autocomplete.SuggestionProvider
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.exception.CommandErrorException
import revxrsal.commands.node.ExecutionContext
import revxrsal.commands.parameter.ParameterType
import revxrsal.commands.stream.MutableStringStream

/**
 * InstalledPluginのParameterType
 * インストール済み管理対象プラグインのIDを解析する
 *
 * Lampフレームワークを使用してカスタムパラメータ型を処理する
 * KoinによるDIでPluginInfoManagerを注入
 */
class InstalledPluginParameterType :
    ParameterType<BukkitCommandActor, InstalledPlugin>,
    KoinComponent {
    // PluginInfoManagerをKoinから注入
    private val pluginInfoManager: PluginInfoManager by inject()

    /**
     * コマンド引数からInstalledPluginを解析する
     * @param input 入力ストリーム
     * @param context 実行コンテキスト
     * @return 解決されたInstalledPlugin
     * @throws CommandErrorException プラグインがインストールされていない場合
     */
    override fun parse(
        input: MutableStringStream,
        context: ExecutionContext<BukkitCommandActor>
    ): InstalledPlugin {
        // 入力ストリームから文字列を読み取る
        val pluginId = input.readString()

        // インストール済み管理対象プラグイン一覧を取得
        val installedPlugins =
            kotlinx.coroutines.runBlocking {
                pluginInfoManager.getAllManagedPlugins().map { pluginData ->
                    when (pluginData) {
                        is PluginData.BukkitPluginData -> pluginData.name
                        is PluginData.PaperPluginData -> pluginData.name
                    }
                }
            }

        // バリデーション: プラグインIDがインストール済み一覧に含まれているかチェック
        if (!installedPlugins.contains(pluginId)) {
            throw CommandErrorException(
                "プラグイン '$pluginId' はインストールされていません。"
            )
        }

        return InstalledPlugin(pluginId)
    }

    /**
     * TAB補完のデフォルトサジェストを提供する
     * @return サジェストプロバイダー
     */
    override fun defaultSuggestions(): SuggestionProvider<BukkitCommandActor> {
        // インストール済み管理対象プラグイン一覧を返すサジェストプロバイダー
        return SuggestionProvider { context ->
            kotlinx.coroutines.runBlocking {
                pluginInfoManager.getAllManagedPlugins().map { pluginData ->
                    when (pluginData) {
                        is PluginData.BukkitPluginData -> pluginData.name
                        is PluginData.PaperPluginData -> pluginData.name
                    }
                }
            }
        }
    }
}