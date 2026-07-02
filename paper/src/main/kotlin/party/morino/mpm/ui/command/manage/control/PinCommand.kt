/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.command.manage.control

import org.bukkit.command.CommandSender
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.application.project.ProjectService
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.domain.plugin.model.PluginSpec
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import party.morino.mpm.api.model.plugin.InstalledPlugin
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.annotation.Switch
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * バージョン固定コマンドのコントローラー
 *
 * mpm.jsonのバージョン指定（"latest", "tag:beta" など）を、
 * 現在インストールされている実バージョン文字列に書き換える。
 * mpm pin <plugin>
 */
@Command("mpm")
@CommandPermission("mpm.command.update")
class PinCommand : KoinComponent {
    // Koinによる依存性注入
    private val metadataManager: PluginMetadataManager by inject()
    private val projectService: ProjectService by inject()

    /**
     * プラグインのバージョン指定を現在インストール済みの固定バージョンに変更する
     *
     * @param sender コマンド送信者
     * @param plugin 対象のインストール済みプラグイン
     */
    @Subcommand("pin")
    suspend fun pin(
        sender: CommandSender,
        plugin: InstalledPlugin
    ) {
        val pluginId = plugin.pluginId

        // メタデータから現在インストールされているバージョンを取得
        val metadata =
            metadataManager.loadMetadata(pluginId).fold(
                ifLeft = { error ->
                    sender.sendRichMessage("<red>メタデータの読み込みに失敗しました: $error</red>")
                    return
                },
                ifRight = { it }
            )

        val currentVersion = metadata.mpmInfo.version.current.raw

        // mpm.jsonのプロジェクトを取得
        val project = projectService.getProject()
        if (project == null) {
            sender.sendRichMessage("<red>プロジェクトが初期化されていません。'mpm init' を実行してください。</red>")
            return
        }

        val name = PluginName(pluginId)
        val currentSpec = project.getPluginSpec(name)

        // unmanagedプラグインや未登録プラグインはピン留め対象外
        if (currentSpec == null || currentSpec is PluginSpec.Unmanaged) {
            sender.sendRichMessage("<red>プラグイン '$pluginId' は managed として登録されていません。</red>")
            return
        }

        // is-check 済みのため smart cast が使える
        val managed = currentSpec as PluginSpec.Managed

        // Sync指定は別プラグインに追従する意図的な設定のため、Fixedで固定化すると同期が壊れる
        syncTargetOf(managed.versionRequirement)?.let { target ->
            sender.sendRichMessage(
                "<red>プラグイン '$pluginId' は '$target' に同期する設定のため固定できません。</red>"
            )
            return
        }

        // 既に同じ固定バージョンならスキップ（smart cast を利用）
        val currentRequirement = managed.versionRequirement
        if (currentRequirement is VersionSpecifier.Fixed &&
            currentRequirement.version == currentVersion
        ) {
            sender.sendRichMessage("<yellow>プラグイン '$pluginId' は既に $currentVersion に固定されています。</yellow>")
            return
        }

        // 変更前のバージョン指定文字列を表示用に作成
        val oldSpecDisplay =
            when (val vs = managed.versionRequirement) {
                is VersionSpecifier.Fixed -> vs.version
                is VersionSpecifier.Latest -> "latest"
                is VersionSpecifier.Tag -> "tag:${vs.tag}"
                is VersionSpecifier.Pattern -> "pattern:${vs.pattern}"
                is VersionSpecifier.Sync -> "sync:${vs.targetPlugin}"
            }

        // Fixed バージョン指定で上書き
        val newSpec = PluginSpec.Managed(name, VersionSpecifier.Fixed(currentVersion))

        project.updatePlugin(name, newSpec).fold(
            ifLeft = { error ->
                sender.sendRichMessage("<red>${error.message}</red>")
            },
            ifRight = { updatedProject ->
                projectService.save(updatedProject.withSortedPlugins()).fold(
                    ifLeft = { error ->
                        sender.sendRichMessage("<red>保存に失敗しました: ${error.message}</red>")
                    },
                    ifRight = {
                        sender.sendRichMessage(
                            "<green>プラグイン '$pluginId' のバージョン指定を固定しました。</green>"
                        )
                        sender.sendRichMessage("<gray>  $oldSpecDisplay → $currentVersion</gray>")
                    }
                )
            }
        )
    }

    /**
     * 管理中の全 managed プラグインを現在インストール済みバージョンに一括固定する
     * mpm pin-all [--dry-run]
     *
     * @param sender コマンド送信者
     * @param dryRun trueの場合、mpm.jsonを変更せずに結果だけ表示する
     */
    @Subcommand("pin-all")
    suspend fun pinAll(
        sender: CommandSender,
        @Switch("dry-run") dryRun: Boolean = false
    ) {
        val project = projectService.getProject()
        if (project == null) {
            sender.sendRichMessage("<red>プロジェクトが初期化されていません。'mpm init' を実行してください。</red>")
            return
        }

        // managed プラグインのみを対象とする
        val managedPlugins = project.plugins.values.filterIsInstance<PluginSpec.Managed>()

        if (managedPlugins.isEmpty()) {
            sender.sendRichMessage("<yellow>ピン留め対象の管理プラグインがありません。</yellow>")
            return
        }

        if (dryRun) {
            sender.sendRichMessage("<gray>[Dry-run] 実際の変更は行いません:</gray>")
        }

        // null チェック済みなので !! で非null型にキャスト
        var updatedProject = project!!
        val pinned = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val skippedSync = mutableListOf<String>()
        val failed = mutableListOf<String>()

        for (spec in managedPlugins) {
            val pluginId = spec.name.value

            // Sync指定は別プラグインに追従する意図的な設定のため、一括固定の対象から除外する
            val syncTarget = syncTargetOf(spec.versionRequirement)
            if (syncTarget != null) {
                skippedSync.add("$pluginId (sync:$syncTarget)")
                continue
            }

            // メタデータから現在インストール済みのバージョンを取得
            val currentVersion =
                metadataManager.loadMetadata(pluginId).fold(
                    ifLeft = { _ ->
                        failed.add(pluginId)
                        null
                    },
                    ifRight = { it.mpmInfo.version.current.raw }
                ) ?: continue

            // 既に同じ Fixed バージョンならスキップ
            val currentRequirement = spec.versionRequirement
            if (currentRequirement is VersionSpecifier.Fixed &&
                currentRequirement.version == currentVersion
            ) {
                skipped.add("$pluginId ($currentVersion)")
                continue
            }

            val oldDisplay =
                when (val vs = spec.versionRequirement) {
                    is VersionSpecifier.Fixed -> vs.version
                    is VersionSpecifier.Latest -> "latest"
                    is VersionSpecifier.Tag -> "tag:${vs.tag}"
                    is VersionSpecifier.Pattern -> "pattern:${vs.pattern}"
                    is VersionSpecifier.Sync -> "sync:${vs.targetPlugin}"
                }
            pinned.add("$pluginId: $oldDisplay → $currentVersion")

            if (!dryRun) {
                // Fixed バージョン指定でプロジェクトを更新
                val newSpec = PluginSpec.Managed(spec.name, VersionSpecifier.Fixed(currentVersion))
                updatedProject =
                    updatedProject.updatePlugin(spec.name, newSpec).fold(
                        ifLeft = { _ ->
                            failed.add(pluginId)
                            pinned.removeLast()
                            updatedProject
                        },
                        ifRight = { it }
                    )
            }
        }

        // dry-run でない場合のみ保存
        if (!dryRun && pinned.isNotEmpty()) {
            projectService.save(updatedProject.withSortedPlugins()).fold(
                ifLeft = { error ->
                    sender.sendRichMessage("<red>保存に失敗しました: ${error.message}</red>")
                    return
                },
                ifRight = { }
            )
        }

        // 結果を表示
        if (pinned.isNotEmpty()) {
            val header =
                if (dryRun) {
                    "<yellow>[Dry-run] ピン留め対象 (${pinned.size}件):</yellow>"
                } else {
                    "<green>ピン留め完了 (${pinned.size}件):</green>"
                }
            sender.sendRichMessage(header)
            pinned.forEach { sender.sendRichMessage("  <green>✓</green> $it") }
        }
        if (skipped.isNotEmpty()) {
            sender.sendRichMessage("<gray>スキップ（既に固定済み）: ${skipped.size}件</gray>")
        }
        if (skippedSync.isNotEmpty()) {
            sender.sendRichMessage("<gray>スキップ（Sync設定のため固定不可）: ${skippedSync.size}件</gray>")
            skippedSync.forEach { sender.sendRichMessage("  <gray>-</gray> $it") }
        }
        if (failed.isNotEmpty()) {
            sender.sendRichMessage("<red>メタデータ取得失敗 (${failed.size}件): ${failed.joinToString(", ")}</red>")
        }
        if (pinned.isEmpty() && failed.isEmpty() && skippedSync.isEmpty()) {
            sender.sendRichMessage("<yellow>全プラグインが既に固定済みです。</yellow>")
        }
    }

    /**
     * バージョン指定がSync（別プラグインへの追従）の場合、その同期先プラグイン名を返す
     * pin / pin-all の両方でSync対象を検出・除外するために使う共通ロジック
     */
    private fun syncTargetOf(versionRequirement: VersionSpecifier): String? =
        (versionRequirement as? VersionSpecifier.Sync)?.targetPlugin
}