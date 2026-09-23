/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
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
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.application.plugin.PluginUpdateService
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.application.plugin.describeTransition
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.annotation.Switch
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * プラグイン更新コマンドのコントローラー
 * プレゼンテーション層とユースケース層の橋渡しを行う
 * mpm update - 新しいバージョンがあるプラグインを更新
 */
@Command("mpm")
@CommandPermission("mpm.command.update")
class UpdateCommand : KoinComponent {
    // Koinによる依存性注入
    private val updateService: PluginUpdateService by inject()
    private val infoService: PluginInfoService by inject()
    private val pluginMetadataManager: PluginMetadataManager by inject()
    private val mpmPlugin: JavaPlugin by inject()

    /**
     * 新しいバージョンがあるプラグインを更新するコマンド
     * @param sender コマンド送信者
     * @param force 必須依存が不足していても強制更新する
     * @param dryRun 更新チェックのみ行い、実際の更新は行わない
     */
    @Subcommand("update")
    suspend fun update(
        sender: CommandSender,
        @Switch("force") force: Boolean = false,
        @Switch("dry-run") dryRun: Boolean = false,
        @Switch("skip-integrity", shorthand = 'k') skipIntegrity: Boolean = false
    ) {
        if (dryRun) {
            executeDryRun(sender)
            return
        }

        // 進捗メッセージをsenderに転送するコールバック
        val progressCallback: (String) -> Unit = { message ->
            sender.sendRichMessage(message)
        }

        // PluginUpdateServiceを実行（force・skip-integrityフラグを伝播）
        updateService.update(force, progressCallback, skipIntegrity).fold(
            // 失敗時の処理
            { error ->
                sender.sendRichMessage("<red>${error.message}</red>")
            },
            // 成功時の処理
            { updateResults ->
                if (updateResults.isEmpty()) {
                    sender.sendRichMessage("<yellow>更新対象のプラグインはありませんでした。</yellow>")
                } else {
                    // 成功・意図的なスキップ・失敗を分ける
                    val successResults = updateResults.filter { it.success }
                    val skippedResults = updateResults.filter { !it.success && it.skipped }
                    val failedResults = updateResults.filter { !it.success && !it.skipped }

                    // 成功した更新を表示
                    if (successResults.isNotEmpty()) {
                        sender.sendRichMessage("<green>以下のプラグインを更新しました:</green>")
                        successResults.forEach { result ->
                            sender.sendRichMessage(
                                "  ✓ ${result.pluginName}: ${result.oldVersion} → ${result.newVersion}"
                            )
                        }
                    }

                    // ロック中などで意図的にスキップしたものを表示（失敗ではない）
                    if (skippedResults.isNotEmpty()) {
                        sender.sendRichMessage("<yellow>以下のプラグインは更新しませんでした:</yellow>")
                        skippedResults.forEach { result ->
                            sender.sendRichMessage(
                                "  - ${result.pluginName}: ${result.errorMessage ?: "スキップしました"}"
                            )
                        }
                    }

                    // 失敗した更新を表示
                    if (failedResults.isNotEmpty()) {
                        sender.sendRichMessage("<red>以下のプラグインの更新に失敗しました:</red>")
                        failedResults.forEach { result ->
                            sender.sendRichMessage("  ✗ ${result.pluginName}: ${result.errorMessage ?: "不明なエラー"}")
                        }
                    }

                    sender.sendRichMessage("<gray>変更を反映するには、サーバーを再起動してください。</gray>")
                }
            }
        )
    }

    /**
     * 指定したプラグインのみを更新するコマンド
     * @param sender コマンド送信者
     * @param plugin 更新対象のプラグイン
     * @param force 必須依存が不足していても強制更新する
     */
    @Subcommand("update")
    suspend fun updateOne(
        sender: CommandSender,
        plugin: InstalledPlugin,
        @Switch("force") force: Boolean = false,
        @Switch("skip-integrity", shorthand = 'k') skipIntegrity: Boolean = false
    ) {
        val pluginId = plugin.pluginId
        sender.sendRichMessage("<gray>'$pluginId' を更新しています...</gray>")

        updateService.update(PluginName(pluginId), force, skipIntegrity).fold(
            { error ->
                sender.sendRichMessage("<red>${error.message}</red>")
            },
            { results ->
                // 更新結果は先頭が親、以降が連動更新した sync: プラグイン（子孫）
                val successResults = results.filter { it.success }
                val skippedResults = results.filter { !it.success && it.skipped }
                val failedResults = results.filter { !it.success && !it.skipped }

                // 成功した更新（親＋連動更新した子）を表示
                if (successResults.isNotEmpty()) {
                    sender.sendRichMessage("<green>以下のプラグインを更新しました:</green>")
                    successResults.forEach { result ->
                        sender.sendRichMessage(
                            "  ✓ ${result.pluginName}: ${result.oldVersion} → ${result.newVersion}"
                        )
                    }
                }

                // ロック中などで意図的にスキップしたものを表示（失敗ではない）
                if (skippedResults.isNotEmpty()) {
                    sender.sendRichMessage("<yellow>以下のプラグインは更新しませんでした:</yellow>")
                    skippedResults.forEach { result ->
                        sender.sendRichMessage("  - ${result.pluginName}: ${result.errorMessage ?: "スキップしました"}")
                    }
                }

                // 失敗した更新を表示
                if (failedResults.isNotEmpty()) {
                    sender.sendRichMessage("<red>以下のプラグインの更新に失敗しました:</red>")
                    failedResults.forEach { result ->
                        sender.sendRichMessage("  ✗ ${result.pluginName}: ${result.errorMessage ?: "不明なエラー"}")
                    }
                }

                if (successResults.isNotEmpty()) {
                    sender.sendRichMessage("<gray>変更を反映するには、サーバーを再起動してください。</gray>")
                }
            }
        )
    }

    /**
     * dry-runモード: 更新チェックのみ行い、結果をプレイヤーに表示する
     *
     * ロック済みプラグインは更新対象から除外して別途表示する
     * @param sender コマンド送信者
     */
    private suspend fun executeDryRun(sender: CommandSender) {
        sender.sendRichMessage("<gray>[Dry-run] プラグインの更新を確認しています...</gray>")

        infoService.checkAllOutdated().fold(
            { error ->
                sender.sendRichMessage("<red>[Dry-run] ${error.message}</red>")
            },
            { result ->
                // チェックに失敗したプラグインを警告表示
                result.errors.forEach { checkError ->
                    sender.sendRichMessage(
                        "<red>[Dry-run] ${checkError.pluginName}: ${checkError.errorMessage}</red>"
                    )
                }

                // 更新が必要なプラグインのみ抽出
                val needsUpdate = result.outdatedPlugins.filter { it.needsUpdate }

                // ロック状態でフィルタリング（実際の更新と同じ条件で表示）
                // メタデータ読み込み失敗はunknownとして警告表示
                val updatable = mutableListOf<String>()
                val locked = mutableListOf<String>()
                val unknown = mutableListOf<String>()
                for (info in needsUpdate) {
                    pluginMetadataManager.loadMetadata(info.pluginName).fold(
                        { unknown.add(info.pluginName) },
                        { metadata ->
                            if (metadata.mpmInfo.settings.lock == true) {
                                locked.add(info.pluginName)
                            } else {
                                updatable.add(info.pluginName)
                            }
                        }
                    )
                }

                val updatableInfos = needsUpdate.filter { it.pluginName in updatable }
                val lockedInfos = needsUpdate.filter { it.pluginName in locked }
                val unknownInfos = needsUpdate.filter { it.pluginName in unknown }
                // 固定バージョンに揃っているが上流にそれより新しい版があるもの（更新では変わらない情報）
                val pinnedBehindInfos = result.outdatedPlugins.filter { !it.needsUpdate && it.hasNewerUpstream }

                if (updatableInfos.isEmpty() &&
                    lockedInfos.isEmpty() &&
                    unknownInfos.isEmpty() &&
                    pinnedBehindInfos.isEmpty() &&
                    result.errors.isEmpty()
                ) {
                    sender.sendRichMessage("<green>[Dry-run] すべてのプラグインは最新です。</green>")
                } else {
                    if (updatableInfos.isNotEmpty()) {
                        sender.sendRichMessage(
                            "<yellow>[Dry-run] ${updatableInfos.size}個のプラグインが更新可能です:</yellow>"
                        )
                        updatableInfos.forEach { info ->
                            sender.sendRichMessage(
                                "  ↑ ${info.pluginName}: ${info.describeTransition()}"
                            )
                        }
                    }
                    if (lockedInfos.isNotEmpty()) {
                        sender.sendRichMessage(
                            "<gray>[Dry-run] ${lockedInfos.size}個のプラグインはロック中です (スキップ):</gray>"
                        )
                        lockedInfos.forEach { info ->
                            sender.sendRichMessage(
                                "  🔒 ${info.pluginName}: ${info.describeTransition()}"
                            )
                        }
                    }
                    if (unknownInfos.isNotEmpty()) {
                        sender.sendRichMessage(
                            "<red>[Dry-run] ${unknownInfos.size}個のプラグインのメタデータ読み込みに失敗:</red>"
                        )
                        unknownInfos.forEach { info ->
                            sender.sendRichMessage("  ⚠ ${info.pluginName}")
                        }
                    }
                    if (pinnedBehindInfos.isNotEmpty()) {
                        sender.sendRichMessage(
                            "<yellow>[Dry-run] ${pinnedBehindInfos.size}個のプラグインは固定バージョンより新しい版があります (更新対象外):</yellow>"
                        )
                        pinnedBehindInfos.forEach { info ->
                            sender.sendRichMessage(
                                "  📌 ${info.pluginName}: ${info.currentVersion} (最新: ${info.latestVersion})"
                            )
                        }
                    }
                    sender.sendRichMessage("<gray>[Dry-run] 実際の更新は行われていません。</gray>")
                }
            }
        )
    }
}