/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.command.manage.lifecycle

import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.application.lock.LockService
import party.morino.mpm.api.application.plugin.PluginUpdateService
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.utils.regenerateQuietly
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Optional
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.annotation.Switch
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * プラグインのバージョン切り戻しコマンドのコントローラー
 * プレゼンテーション層とユースケース層の橋渡しを行う
 * mpm rollback <plugin> [version] - 指定プラグインを過去のバージョンへ戻す
 */
@Command("mpm")
@CommandPermission("mpm.command.rollback")
class RollbackCommand : KoinComponent {
    // Koinによる依存性注入
    private val updateService: PluginUpdateService by inject()
    private val lockService: LockService by inject()
    private val mpmPlugin: JavaPlugin by inject()

    /**
     * プラグインを過去のバージョンへ切り戻すコマンド
     *
     * バージョンを省略した場合はメタデータの履歴から直前のバージョンを解決する。
     * 実行前には自動でバックアップが作成される。
     *
     * @param sender コマンド送信者
     * @param plugin 切り戻し対象のプラグイン
     * @param version 切り戻し先バージョン（省略時は履歴上の直前のバージョン）
     * @param force ロック済み・api-version非互換でも強制的に切り戻す
     * @param skipIntegrity 整合性検証の不一致を無視して続行する
     */
    @Subcommand("rollback")
    suspend fun rollback(
        sender: CommandSender,
        plugin: InstalledPlugin,
        @Optional version: String? = null,
        @Switch("force") force: Boolean = false,
        @Switch("skip-integrity", shorthand = 'k') skipIntegrity: Boolean = false
    ) {
        val pluginId = plugin.pluginId

        // 対象バージョンの指定有無で案内文を変える（省略時はサービス側が履歴から解決する）
        val target = version?.let { "バージョン $it" } ?: "直前のバージョン"
        sender.sendRichMessage("<gray>'$pluginId' を${target}に戻しています...</gray>")

        updateService.rollback(PluginName(pluginId), version, force, skipIntegrity).fold(
            // 失敗時の処理
            { error ->
                sender.sendRichMessage("<red>${error.message}</red>")
            },
            // 成功時の処理
            { result ->
                sender.sendRichMessage("<green>'$pluginId' を切り戻しました。</green>")
                sender.sendRichMessage("<gray>  ${result.oldVersion} → ${result.newVersion}</gray>")
                sender.sendRichMessage(
                    "<gray>mpm.json のバージョン指定を ${result.newVersion} に固定しました。</gray>"
                )
                sender.sendRichMessage("<gray>変更を反映するには、サーバーを再起動してください。</gray>")

                // 監査用に切り戻し内容をサーバーログにも残す
                mpmPlugin.logger.info("[rollback] $pluginId: ${result.oldVersion} -> ${result.newVersion}")
            }
        )

        // 成否に関わらず、ロックファイルを実際のインストール状態へ追従させる
        lockService.regenerateQuietly(mpmPlugin.logger)
    }
}