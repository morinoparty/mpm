/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
 * and related and neighboring rights to this software to the public domain worldwide.
 * This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.command.manage

import org.bukkit.command.CommandSender
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.application.model.PluginFilter
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.application.plugin.PluginLifecycleService
import party.morino.mpm.api.domain.repository.RepositoryManager
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.annotation.Switch
import revxrsal.commands.bukkit.annotation.CommandPermission

/**
 * unmanagedプラグインのadoptコマンド
 *
 * すべてのunmanagedプラグインをリポジトリから検索し、
 * 見つかった場合はmanaged状態に変更してダウンロードする
 *
 * mpm adopt           - すべてのunmanagedプラグインをadopt
 * mpm adopt --dry-run - 対象プラグインを表示のみ（実行しない）
 * mpm adopt --soft    - softDependenciesも含める
 * mpm adopt --pin     - 既存JARのバージョンに固定してadopt
 */
@Command("mpm")
@CommandPermission("mpm.command.add")
class AdoptCommand : KoinComponent {
    // KoinによるDI
    private val lifecycleService: PluginLifecycleService by inject()
    private val infoService: PluginInfoService by inject()
    private val repositoryManager: RepositoryManager by inject()

    /**
     * すべてのunmanagedプラグインをリポジトリから検索してadoptする
     *
     * @param sender コマンド送信者
     * @param soft softDependenciesも含める場合はtrue
     * @param dryRun 実行せず対象プラグインを表示のみの場合はtrue
     * @param pin 既存JARのバージョンに固定する場合はtrue
     */
    @Subcommand("adopt")
    suspend fun adopt(
        sender: CommandSender,
        @Switch("soft") soft: Boolean = false,
        @Switch("dry-run") dryRun: Boolean = false,
        @Switch("pin") pin: Boolean = false
    ) {
        if (dryRun) {
            // dry-runモード: 対象プラグインと依存関係を表示のみ
            executeDryRun(sender, pin, soft)
        } else {
            // 通常実行: adoptを実行
            executeAdopt(sender, soft, pin)
        }
    }

    /**
     * dry-runモード: 対象プラグインと依存関係解析を表示
     *
     * @param sender コマンド送信者
     * @param pin バージョン固定モード表示
     * @param includeSoft softDependenciesも含めるか
     */
    private suspend fun executeDryRun(sender: CommandSender, pin: Boolean, includeSoft: Boolean) {
        sender.sendRichMessage("<gray>unmanagedプラグインをリポジトリから検索しています...")

        // unmanagedプラグイン一覧を取得
        val unmanagedPlugins = infoService.list(PluginFilter.UNMANAGED)
        val unmanagedNames = unmanagedPlugins.map { it.name.value }

        if (unmanagedNames.isEmpty()) {
            sender.sendRichMessage("<yellow>unmanagedプラグインはありません。")
            return
        }

        // 管理済みプラグイン一覧を取得（依存チェック用）
        val managedPlugins = infoService.list(PluginFilter.MANAGED)
        val managedNamesLower = managedPlugins.map { it.name.value.lowercase() }.toSet()

        // リポジトリの利用可能なプラグイン名マップ（lowercase → canonical name）
        val availablePlugins = repositoryManager.getAvailablePlugins()
        val availablePluginsMap = availablePlugins.associateBy { it.lowercase() }

        // マッチングを行う（canonical name を保持）
        val matchedPlugins = mutableListOf<Pair<String, String>>() // unmanagedName to repoName
        val skippedPlugins = mutableListOf<String>()

        for (name in unmanagedNames) {
            val repoName = availablePluginsMap[name.lowercase()]
            if (repoName != null) {
                matchedPlugins.add(name to repoName)
            } else {
                skippedPlugins.add(name)
            }
        }

        // adopt対象のプラグイン名セット（依存チェックで「adoptされる予定」を判定するため）
        val willBeAdoptedLower = matchedPlugins.map { it.second.lowercase() }.toSet()

        // 全リポジトリファイルを事前取得（suspend呼び出しをここで完了させる）
        val repoFileCache = mutableMapOf<String, party.morino.mpm.api.domain.repository.RepositoryFile?>()
        for ((_, repoName) in matchedPlugins) {
            prefetchRepoDependencies(repoName, repoFileCache, includeSoft)
        }

        // 依存関係の警告を蓄積
        val notFoundDependencies = mutableSetOf<Pair<String, String>>() // depName to requiredBy

        // 結果を表示
        if (matchedPlugins.isNotEmpty()) {
            val modeLabel = if (pin) "adoptされるプラグイン (pin)" else "adoptされるプラグイン"
            sender.sendRichMessage("<green>===== $modeLabel (${matchedPlugins.size}) =====")
            val visited = mutableSetOf<String>()
            matchedPlugins.forEach { (unmanagedName, repoName) ->
                val pinInfo = if (pin) " <aqua>(バージョン固定を試行)</aqua>" else ""
                sender.sendRichMessage("<green>-<reset> $unmanagedName$pinInfo")

                // canonical name で再帰的に依存解析（キャッシュ済みデータを使用）
                visited.add(repoName.lowercase())
                displayDependencyTree(
                    sender, repoName, 1, includeSoft, repoFileCache,
                    managedNamesLower, willBeAdoptedLower, availablePluginsMap,
                    visited, notFoundDependencies
                )
            }
        }

        if (skippedPlugins.isNotEmpty()) {
            sender.sendRichMessage("<yellow>===== リポジトリに見つからない (${skippedPlugins.size}) =====")
            skippedPlugins.forEach { name ->
                sender.sendRichMessage("<yellow>~<reset> $name")
            }
        }

        // 見つからない必須依存の警告
        if (notFoundDependencies.isNotEmpty()) {
            sender.sendRichMessage("<red>===== 依存関係の警告 (${notFoundDependencies.size}) =====")
            notFoundDependencies.forEach { (dep, requiredBy) ->
                sender.sendRichMessage("<red>!<reset> $dep <gray>← $requiredBy が依存")
            }
        }

        sender.sendRichMessage("<gray>-----")
        sender.sendRichMessage("<gray>実行するには --dry-run を外してください")
    }

    /**
     * 通常実行: adoptを実行
     */
    private suspend fun executeAdopt(
        sender: CommandSender,
        includeSoftDependencies: Boolean,
        pinToCurrentVersion: Boolean
    ) {
        sender.sendRichMessage("<gray>unmanagedプラグインをリポジトリから検索しています...")
        if (pinToCurrentVersion) {
            sender.sendRichMessage("<aqua>--pin: 既存JARのバージョンに固定します")
        }

        lifecycleService.adoptAll(includeSoftDependencies, pinToCurrentVersion).fold(
            { error ->
                sender.sendRichMessage("<red>${error.message}")
            },
            { result ->
                // adoptされたプラグインを表示
                if (result.adoptedPlugins.isNotEmpty()) {
                    sender.sendRichMessage("<green>===== adoptされたプラグイン (${result.adoptedPlugins.size}) =====")
                    result.adoptedPlugins.forEach { addResult ->
                        val depMark = if (addResult.isDependency) "<gray>[依存]</gray> " else ""
                        displayInstallResult(sender, addResult.installResult, depMark)
                    }
                }

                // バージョン固定されたプラグインを表示
                if (result.pinnedPlugins.isNotEmpty()) {
                    sender.sendRichMessage("<aqua>===== バージョン固定 (${result.pinnedPlugins.size}) =====")
                    result.pinnedPlugins.forEach { name ->
                        sender.sendRichMessage("<aqua>📌<reset> $name")
                    }
                }

                // ハッシュ不一致の警告を表示
                if (result.hashMismatchWarnings.isNotEmpty()) {
                    sender.sendRichMessage("<yellow>===== ハッシュ検証の警告 (${result.hashMismatchWarnings.size}) =====")
                    result.hashMismatchWarnings.forEach { (name, warning) ->
                        sender.sendRichMessage("<yellow>⚠<reset> $name: $warning")
                    }
                }

                // リポジトリに見つからなかったプラグインを表示
                if (result.skippedPlugins.isNotEmpty()) {
                    sender.sendRichMessage("<yellow>===== リポジトリに見つからない (${result.skippedPlugins.size}) =====")
                    result.skippedPlugins.forEach { name ->
                        sender.sendRichMessage("<yellow>~<reset> $name <gray>(手動管理のまま)")
                    }
                }

                // 見つからなかった依存プラグインを表示
                if (result.notFoundDependencies.isNotEmpty()) {
                    sender.sendRichMessage("<red>===== 依存関係が見つからない (${result.notFoundDependencies.size}) =====")
                    result.notFoundDependencies.forEach { name ->
                        sender.sendRichMessage("<red>!<reset> $name <gray>(手動でインストールが必要)")
                    }
                }

                // 失敗したプラグインを表示
                if (result.failedPlugins.isNotEmpty()) {
                    sender.sendRichMessage("<red>===== adopt失敗 (${result.failedPlugins.size}) =====")
                    result.failedPlugins.forEach { (name, error) ->
                        sender.sendRichMessage("<red>✗<reset> $name: $error")
                    }
                }

                // サマリー
                sender.sendRichMessage("<gray>-----")
                sender.sendRichMessage(
                    "<gray>合計: " +
                        "<green>${result.adoptedPlugins.size}個adopt</green>, " +
                        "<yellow>${result.skippedPlugins.size}個スキップ</yellow>, " +
                        "<red>${result.failedPlugins.size}個失敗</red>"
                )

                if (result.adoptedPlugins.isNotEmpty()) {
                    sender.sendRichMessage("<gray>変更を反映するには、サーバーを再起動してください。")
                }
            }
        )
    }

    /**
     * リポファイルを再帰的に事前取得してキャッシュに格納する
     *
     * @param pluginName プラグイン名
     * @param cache キャッシュマップ（lowercase name → RepositoryFile?）
     * @param includeSoft softDependenciesも辿るか
     */
    private suspend fun prefetchRepoDependencies(
        pluginName: String,
        cache: MutableMap<String, party.morino.mpm.api.domain.repository.RepositoryFile?>,
        includeSoft: Boolean
    ) {
        val key = pluginName.lowercase()
        if (cache.containsKey(key)) return

        val repoFile = repositoryManager.getRepositoryFile(pluginName)
        cache[key] = repoFile
        if (repoFile == null) return

        // 依存を再帰的に事前取得
        val deps = repoFile.dependencies.toMutableList()
        if (includeSoft) deps.addAll(repoFile.softDependencies)
        for (dep in deps) {
            prefetchRepoDependencies(dep, cache, includeSoft)
        }
    }

    /**
     * 依存ツリーを再帰的に表示する（キャッシュ済みデータを使用、suspendなし）
     */
    private fun displayDependencyTree(
        sender: CommandSender,
        pluginName: String,
        depth: Int,
        includeSoft: Boolean,
        cache: Map<String, party.morino.mpm.api.domain.repository.RepositoryFile?>,
        managedNamesLower: Set<String>,
        willBeAdoptedLower: Set<String>,
        availablePluginsMap: Map<String, String>,
        visited: MutableSet<String>,
        notFoundDependencies: MutableSet<Pair<String, String>>
    ) {
        val repoFile = cache[pluginName.lowercase()] ?: return
        val deps = repoFile.dependencies.toMutableList()
        if (includeSoft) deps.addAll(repoFile.softDependencies)

        val indent = "  ".repeat(depth)
        for (dep in deps) {
            val depLower = dep.lowercase()
            val isSoft = dep in repoFile.softDependencies
            val label = if (isSoft) "<gray>[soft]</gray> " else ""
            when {
                managedNamesLower.contains(depLower) ->
                    sender.sendRichMessage("$indent<gray>└ ${label}$dep <green>(管理済み)")
                willBeAdoptedLower.contains(depLower) ->
                    sender.sendRichMessage("$indent<gray>└ ${label}$dep <aqua>(adopt予定)")
                availablePluginsMap.containsKey(depLower) -> {
                    sender.sendRichMessage("$indent<gray>└ ${label}$dep <blue>(リポジトリあり・依存として追加)")
                    // 再帰的にこの依存の依存も解析（循環防止）
                    if (visited.add(depLower)) {
                        displayDependencyTree(
                            sender, dep, depth + 1, includeSoft, cache,
                            managedNamesLower, willBeAdoptedLower, availablePluginsMap,
                            visited, notFoundDependencies
                        )
                    }
                }
                else -> {
                    sender.sendRichMessage("$indent<gray>└ ${label}$dep <red>(見つかりません)")
                    if (!isSoft) notFoundDependencies.add(dep to pluginName)
                }
            }
        }
    }

    /**
     * インストール結果を表示するヘルパーメソッド
     */
    private fun displayInstallResult(
        sender: CommandSender,
        installResult: party.morino.mpm.api.application.model.InstallResult,
        prefix: String = ""
    ) {
        // 削除されたプラグイン情報を表示
        installResult.removed?.let { removedInfo ->
            sender.sendRichMessage("<red>-<reset> ${removedInfo.name} <gray>${removedInfo.version}")
        }

        // インストールされたプラグイン情報を表示
        val versionInfo =
            if (installResult.installed.latestVersion.isEmpty() ||
                installResult.installed.currentVersion == installResult.installed.latestVersion
            ) {
                installResult.installed.currentVersion
            } else {
                "${installResult.installed.currentVersion} (${installResult.installed.latestVersion}が有効)"
            }
        sender.sendRichMessage("$prefix<green>+<reset> ${installResult.installed.name} <gray>$versionInfo")
    }
}
