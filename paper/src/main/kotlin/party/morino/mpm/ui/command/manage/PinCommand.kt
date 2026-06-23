/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.command.manage

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
}