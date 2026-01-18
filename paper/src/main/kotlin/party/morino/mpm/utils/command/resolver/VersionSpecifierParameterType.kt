/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils.command.resolver

import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.config.plugin.VersionSpecifier
import party.morino.mpm.api.config.plugin.VersionSpecifierParser
import party.morino.mpm.api.core.plugin.PluginInfoManager
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import revxrsal.commands.autocomplete.SuggestionProvider
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.node.ExecutionContext
import revxrsal.commands.parameter.ParameterType
import revxrsal.commands.stream.MutableStringStream

/**
 * VersionSpecifierのParameterType
 * コマンドライン引数からバージョン指定を解析する
 *
 * Lampフレームワークを使用してカスタムパラメータ型を処理する
 */
class VersionSpecifierParameterType :
    ParameterType<BukkitCommandActor, VersionSpecifier>,
    KoinComponent {
    // PluginInfoManagerをKoinから注入
    private val infoManager: PluginInfoManager by inject()

    /**
     * コマンド引数からVersionSpecifierを解析する
     * @param input 入力ストリーム
     * @param context 実行コンテキスト
     * @return 解決されたVersionSpecifier
     */
    override fun parse(
        input: MutableStringStream,
        context: ExecutionContext<BukkitCommandActor>
    ): VersionSpecifier {
        // 入力ストリームから文字列を読み取る
        val versionString = input.readString()

        // VersionSpecifierParserを使用してパース
        // "latest", "sync:PluginName", "tag:stable", "pattern:..." などをサポート
        return VersionSpecifierParser.parse(versionString)
    }

    /**
     * TAB補完のデフォルトサジェストを提供する
     * @return サジェストプロバイダー
     */
    override fun defaultSuggestions(): SuggestionProvider<BukkitCommandActor> {
        return SuggestionProvider { ctx ->
            val plugin = ctx.getResolvedArgumentOrNull(RepositoryPlugin::class.java)
            return@SuggestionProvider plugin?.let {
                // プラグインの利用可能なバージョンを取得
                // Note: getVersions()はsuspend関数のため、runBlockingを使用
                val versions =
                    runBlocking {
                        infoManager.getVersions(plugin).fold(
                            // エラーの場合は空リストを返す
                            {
                                emptyList()
                            },
                            // 成功した場合はバージョンリストを返す
                            { it }
                        )
                    }

                // バージョン文字列のリスト（新しい順）と"latest"を返す
                versions + listOf("latest")
            } ?: listOf("latest")
        }
    }
}