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
import party.morino.mpm.api.core.repository.RepositoryManager
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import revxrsal.commands.autocomplete.SuggestionProvider
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.exception.CommandErrorException
import revxrsal.commands.node.ExecutionContext
import revxrsal.commands.parameter.ParameterType
import revxrsal.commands.stream.MutableStringStream

/**
 * RepositoryPluginのParameterType
 * リポジトリから取得可能なプラグインIDを解析する
 *
 * Lampフレームワークを使用してカスタムパラメータ型を処理する
 * KoinによるDIでRepositoryManagerを注入
 */
class RepositoryPluginParameterType :
    ParameterType<BukkitCommandActor, RepositoryPlugin>,
    KoinComponent {
    // RepositoryManagerをKoinから注入
    private val repositoryManager: RepositoryManager by inject()

    /**
     * コマンド引数からRepositoryPluginを解析する
     * @param input 入力ストリーム
     * @param context 実行コンテキスト
     * @return 解決されたRepositoryPlugin
     * @throws CommandErrorException プラグインがリポジトリに見つからない場合
     */
    override fun parse(
        input: MutableStringStream,
        context: ExecutionContext<BukkitCommandActor>
    ): RepositoryPlugin {
        // 入力ストリームから文字列を読み取る
        val pluginId = input.readString()

        // リポジトリから取得可能なプラグイン一覧を取得
        // Note: RepositoryManager.getAvailablePlugins()はsuspend関数
        // ParameterType.parse()はsuspend関数ではないため、runBlockingを使用
        val availablePlugins =
            kotlinx.coroutines.runBlocking {
                repositoryManager.getAvailablePlugins()
            }

        // バリデーション: プラグインIDが利用可能なプラグイン一覧に含まれているかチェック
        if (!availablePlugins.contains(pluginId)) {
            throw CommandErrorException(
                "プラグイン '$pluginId' はリポジトリに見つかりませんでした。"
            )
        }

        return RepositoryPlugin(pluginId)
    }

    /**
     * TAB補完のデフォルトサジェストを提供する
     * @return サジェストプロバイダー
     */
    override fun defaultSuggestions(): SuggestionProvider<BukkitCommandActor> {
        // リポジトリから取得可能なプラグイン一覧を返すサジェストプロバイダー
        return SuggestionProvider { context ->
            kotlinx.coroutines.runBlocking {
                repositoryManager.getAvailablePlugins().toList()
            }
        }
    }
}