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

package party.morino.mpm.api.domain.project.model

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.domain.plugin.model.PluginSpec
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.domain.project.dto.MpmConfig
import party.morino.mpm.api.shared.error.MpmError

/**
 * MPMプロジェクトを表すアグリゲートルート
 *
 * mpm.jsonファイルを表し、プロジェクト全体のプラグイン依存関係を管理する
 * ManagedPluginとは別のアグリゲートとして機能する
 */
class MpmProject private constructor(
    val name: String,
    val version: String,
    private val pluginMap: MutableMap<PluginName, PluginSpec>
) {
    /**
     * プラグイン一覧（読み取り専用）
     */
    val plugins: Map<PluginName, PluginSpec> get() = pluginMap.toMap()

    /**
     * プラグインを追加する
     *
     * @param spec プラグイン指定
     * @return 更新されたプロジェクト、または既存の場合はエラー
     */
    fun addPlugin(spec: PluginSpec): Either<MpmError, MpmProject> {
        if (pluginMap.containsKey(spec.name)) {
            return MpmError.PluginError.AlreadyExists(spec.name.value).left()
        }
        val newPlugins = pluginMap.toMutableMap()
        newPlugins[spec.name] = spec
        return MpmProject(name, version, newPlugins).right()
    }

    /**
     * プラグインを削除する
     *
     * @param name プラグイン名
     * @return 更新されたプロジェクト、または存在しない場合はエラー
     */
    fun removePlugin(name: PluginName): Either<MpmError, MpmProject> {
        if (!pluginMap.containsKey(name)) {
            return MpmError.PluginError.NotFound(name.value).left()
        }
        val newPlugins = pluginMap.toMutableMap()
        newPlugins.remove(name)
        return MpmProject(this.name, version, newPlugins).right()
    }

    /**
     * プラグイン指定を取得する
     *
     * @param name プラグイン名
     * @return プラグイン指定（存在しない場合はnull）
     */
    fun getPluginSpec(name: PluginName): PluginSpec? = pluginMap[name]

    /**
     * プラグインのバージョン指定を更新する
     *
     * @param name プラグイン名
     * @param spec 新しいプラグイン指定
     * @return 更新されたプロジェクト
     */
    fun updatePlugin(
        name: PluginName,
        spec: PluginSpec
    ): Either<MpmError, MpmProject> {
        if (!pluginMap.containsKey(name)) {
            return MpmError.PluginError.NotFound(name.value).left()
        }
        val newPlugins = pluginMap.toMutableMap()
        newPlugins[name] = spec
        return MpmProject(this.name, version, newPlugins).right()
    }

    // ===== Sync依存関係バリデーション =====

    /**
     * Sync依存関係のバリデーションを行う
     *
     * 以下のエラーをチェックする:
     * - ターゲットプラグインが存在しない
     * - ターゲットがunmanagedである
     * - ターゲットもSync指定である
     * - 循環依存がある
     */
    fun validateSyncDependencies(): Either<MpmError, Unit> {
        // 各プラグインのSync依存関係をチェック
        for ((pluginName, spec) in pluginMap) {
            // Managed以外はスキップ
            val managed = spec as? PluginSpec.Managed ?: continue
            // Sync以外はスキップ
            val sync = managed.versionRequirement as? VersionSpecifier.Sync ?: continue

            val targetName = PluginName(sync.targetPlugin)
            val targetSpec = pluginMap[targetName]

            // ターゲットプラグインが存在しない場合はエラー
            if (targetSpec == null) {
                return MpmError.ProjectError
                    .SyncDependencyError(
                        "Plugin '${pluginName.value}' syncs to '${sync.targetPlugin}' which does not exist"
                    ).left()
            }

            // ターゲットがUnmanagedの場合はエラー
            if (targetSpec is PluginSpec.Unmanaged) {
                return MpmError.ProjectError
                    .SyncDependencyError(
                        "Plugin '${pluginName.value}' syncs to '${sync.targetPlugin}' which is unmanaged"
                    ).left()
            }

            // ターゲットもSync指定の場合はエラー
            val targetManaged = targetSpec as? PluginSpec.Managed
            if (targetManaged?.versionRequirement is VersionSpecifier.Sync) {
                return MpmError.ProjectError
                    .SyncDependencyError(
                        "Plugin '${pluginName.value}' syncs to '${sync.targetPlugin}' which also uses sync"
                    ).left()
            }
        }

        // 循環依存をチェック
        detectCircularDependencies()?.let { cycle ->
            return MpmError.ProjectError.CircularDependency(cycle.map { it.value }).left()
        }

        return Unit.right()
    }

    /**
     * Sync依存関係における循環依存を検出する
     *
     * DFS（深さ優先探索）を使用して循環を検出する
     *
     * @return 循環が見つかった場合は循環を構成するプラグイン名のリスト、見つからない場合はnull
     */
    private fun detectCircularDependencies(): List<PluginName>? {
        // Sync依存関係のグラフを構築
        val syncGraph = mutableMapOf<PluginName, PluginName>()
        for ((pluginName, spec) in pluginMap) {
            val managed = spec as? PluginSpec.Managed ?: continue
            val sync = managed.versionRequirement as? VersionSpecifier.Sync ?: continue
            syncGraph[pluginName] = PluginName(sync.targetPlugin)
        }

        val visited = mutableSetOf<PluginName>()
        val path = mutableListOf<PluginName>()
        val inPath = mutableSetOf<PluginName>()

        fun dfs(node: PluginName): List<PluginName>? {
            if (node in inPath) {
                val cycleStart = path.indexOf(node)
                return path.subList(cycleStart, path.size) + node
            }

            if (node in visited) {
                return null
            }

            visited.add(node)
            path.add(node)
            inPath.add(node)

            syncGraph[node]?.let { neighbor ->
                dfs(neighbor)?.let { cycle ->
                    return cycle
                }
            }

            path.removeLast()
            inPath.remove(node)

            return null
        }

        for (node in syncGraph.keys) {
            if (node !in visited) {
                dfs(node)?.let { cycle ->
                    return cycle
                }
            }
        }

        return null
    }

    /**
     * Sync依存関係を考慮したトポロジカルソートを行う
     *
     * Kahnのアルゴリズムを使用して、依存先が先に来るようにプラグインをソートする
     *
     * @return ソートされたプラグイン指定のリスト
     */
    fun topologicalSort(): Either<MpmError, List<PluginSpec>> {
        // まずバリデーション
        validateSyncDependencies().fold(
            { error -> return error.left() },
            { /* OK */ }
        )

        // Sync依存関係のグラフを構築（逆方向: 依存先 → 依存元）
        val dependents = mutableMapOf<PluginName, MutableList<PluginName>>()
        val inDegree = mutableMapOf<PluginName, Int>()

        // 全プラグインを初期化
        for (pluginName in pluginMap.keys) {
            inDegree[pluginName] = 0
            dependents[pluginName] = mutableListOf()
        }

        // Sync依存関係を構築
        for ((pluginName, spec) in pluginMap) {
            val managed = spec as? PluginSpec.Managed ?: continue
            val sync = managed.versionRequirement as? VersionSpecifier.Sync ?: continue
            val target = PluginName(sync.targetPlugin)

            if (pluginMap.containsKey(target)) {
                dependents.getOrPut(target) { mutableListOf() }.add(pluginName)
                inDegree[pluginName] = inDegree.getOrDefault(pluginName, 0) + 1
            }
        }

        // 入次数が0のノードをキューに追加
        val queue = ArrayDeque<PluginName>()
        for ((pluginName, degree) in inDegree) {
            if (degree == 0) {
                queue.add(pluginName)
            }
        }

        // トポロジカルソートを実行
        val result = mutableListOf<PluginSpec>()
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            pluginMap[current]?.let { result.add(it) }

            for (dependent in dependents[current] ?: emptyList()) {
                val newDegree = inDegree.getOrDefault(dependent, 1) - 1
                inDegree[dependent] = newDegree
                if (newDegree == 0) {
                    queue.add(dependent)
                }
            }
        }

        // 残りのノードを追加
        val remaining = pluginMap.keys.filter { name -> result.none { it.name == name } }
        for (name in remaining) {
            pluginMap[name]?.let { result.add(it) }
        }

        return result.right()
    }

    /**
     * プラグイン一覧をアルファベット順でソートした新しいプロジェクトを返す
     */
    fun withSortedPlugins(): MpmProject {
        val sortedPlugins = pluginMap.toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it.value })
        return MpmProject(name, version, sortedPlugins)
    }

    // ===== DTO変換 =====

    /**
     * DTOに変換（永続化用）
     */
    fun toDto(): MpmConfig {
        val pluginsMap =
            pluginMap.mapKeys { it.key.value }.mapValues { (_, spec) ->
                when (spec) {
                    is PluginSpec.Unmanaged -> "unmanaged"
                    is PluginSpec.Managed ->
                        when (val vs = spec.versionRequirement) {
                            is VersionSpecifier.Fixed -> vs.version
                            is VersionSpecifier.Latest -> "latest"
                            is VersionSpecifier.Tag -> "tag:${vs.tag}"
                            is VersionSpecifier.Pattern -> "pattern:${vs.pattern}"
                            is VersionSpecifier.Sync -> "sync:${vs.targetPlugin}"
                        }
                }
            }
        return MpmConfig(name = name, version = version, plugins = pluginsMap)
    }

    companion object {
        /**
         * DTOからプロジェクトを生成
         *
         * @param dto MpmConfig
         * @param parseVersionSpecifier バージョン文字列をVersionSpecifierにパースする関数
         */
        fun fromDto(
            dto: MpmConfig,
            parseVersionSpecifier: (String) -> VersionSpecifier
        ): MpmProject {
            val plugins =
                dto.plugins
                    .map { (name, versionString) ->
                        val pluginName = PluginName(name)
                        val spec =
                            if (versionString == "unmanaged") {
                                PluginSpec.Unmanaged(pluginName)
                            } else {
                                PluginSpec.Managed(pluginName, parseVersionSpecifier(versionString))
                            }
                        pluginName to spec
                    }.toMap()
                    .toMutableMap()

            return MpmProject(dto.name, dto.version, plugins)
        }

        /**
         * 新規プロジェクトを作成
         */
        fun create(
            name: String,
            version: String = "1.0.0"
        ): MpmProject = MpmProject(name, version, mutableMapOf())
    }
}