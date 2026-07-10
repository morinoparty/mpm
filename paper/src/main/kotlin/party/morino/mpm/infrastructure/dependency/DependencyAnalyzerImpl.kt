/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.dependency

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.dependency.DependencyAnalyzer
import party.morino.mpm.api.model.dependency.DependencyError
import party.morino.mpm.api.model.dependency.DependencyInfo
import party.morino.mpm.api.model.dependency.DependencyNode
import party.morino.mpm.api.model.dependency.DependencyTree
import party.morino.mpm.api.model.plugin.PluginData
import party.morino.mpm.utils.PluginDataUtils

/**
 * プラグインの依存関係を解析する実装クラス
 * plugins/ディレクトリ内のjarファイルから依存関係情報を収集し、ツリー構造で表示する
 */
class DependencyAnalyzerImpl :
    DependencyAnalyzer,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()

    // プラグインデータのキャッシュ（プラグイン名 -> PluginData）
    private val pluginDataCache: MutableMap<String, PluginData> = mutableMapOf()

    // 最後にキャッシュを更新した時刻
    private var lastCacheUpdate: Long = 0

    // キャッシュの有効期限（30秒）
    private val cacheExpirationMs = 30_000L

    /**
     * 指定されたプラグインの依存関係情報を取得する
     *
     * @param pluginName プラグイン名
     * @return 成功時はDependencyInfo、失敗時はDependencyError
     */
    override fun getDependencyInfo(pluginName: String): Either<DependencyError, DependencyInfo> {
        refreshCacheIfNeeded()

        val pluginData =
            pluginDataCache[pluginName]
                ?: return DependencyError.PluginNotFound(pluginName).left()

        return extractDependencyInfo(pluginName, pluginData).right()
    }

    /**
     * 指定されたプラグインの依存関係ツリーを構築する
     *
     * @param pluginName プラグイン名
     * @param includeSoftDependencies softDependも含めるかどうか
     * @return 成功時はDependencyTree、失敗時はDependencyError
     */
    override fun buildDependencyTree(
        pluginName: String,
        includeSoftDependencies: Boolean
    ): Either<DependencyError, DependencyTree> {
        refreshCacheIfNeeded()

        val pluginData =
            pluginDataCache[pluginName]
                ?: return DependencyError.PluginNotFound(pluginName).left()

        // 循環依存を検出するための訪問済みセット
        val visited = mutableSetOf<String>()

        // ルートノードを構築
        val rootNode = buildNode(pluginName, pluginData, includeSoftDependencies, visited, isRequired = true)

        // 不足している依存を収集
        val missingRequired = mutableListOf<String>()
        val missingSoft = mutableListOf<String>()

        collectMissingDependencies(rootNode, missingRequired, missingSoft)

        return DependencyTree(
            root = rootNode,
            missingRequired = missingRequired.distinct(),
            missingSoft = missingSoft.distinct()
        ).right()
    }

    /**
     * 不足している依存関係をチェックする
     *
     * @param pluginName プラグイン名（nullの場合は全プラグインをチェック）
     * @return 成功時はプラグイン名と不足している依存関係のマップ、pluginNameが未インストールの場合はDependencyError
     */
    override fun checkMissingDependencies(pluginName: String?): Either<DependencyError, Map<String, List<String>>> {
        refreshCacheIfNeeded()

        val installedPlugins = pluginDataCache.keys

        // pluginNameが指定されているのにキャッシュに存在しない場合は、他のAPIと同様にエラーを返す
        val pluginsToCheck =
            if (pluginName != null) {
                if (!pluginDataCache.containsKey(pluginName)) {
                    return DependencyError.PluginNotFound(pluginName).left()
                }
                listOf(pluginName)
            } else {
                installedPlugins.toList()
            }

        val result = mutableMapOf<String, List<String>>()
        for (plugin in pluginsToCheck) {
            val pluginData = pluginDataCache[plugin] ?: continue
            val dependencyInfo = extractDependencyInfo(plugin, pluginData)

            // 必須依存のみチェック
            val missingDeps =
                dependencyInfo.depend.filter { dep ->
                    !installedPlugins.contains(dep)
                }

            if (missingDeps.isNotEmpty()) {
                result[plugin] = missingDeps
            }
        }

        return result.right()
    }

    /**
     * 指定されたプラグインに依存しているプラグイン（逆依存）を取得する
     *
     * @param pluginName プラグイン名
     * @return 成功時はこのプラグインに依存しているプラグインのリスト、失敗時はDependencyError
     */
    override fun getReverseDependencies(pluginName: String): Either<DependencyError, List<String>> {
        refreshCacheIfNeeded()

        // 存在しないプラグイン名の場合は、他のAPIと同様にエラーを返す
        if (!pluginDataCache.containsKey(pluginName)) {
            return DependencyError.PluginNotFound(pluginName).left()
        }

        val dependents = mutableListOf<String>()

        for ((name, data) in pluginDataCache) {
            if (name == pluginName) continue

            val info = extractDependencyInfo(name, data)
            // 必須依存とオプション依存の両方をチェック
            if (info.depend.contains(pluginName) || info.softDepend.contains(pluginName)) {
                dependents.add(name)
            }
        }

        return dependents.right()
    }

    /**
     * 指定プラグインへの依存チェーンを取得する（`mpm deps why` 用）
     *
     * 逆依存グラフを遡り、他のどのプラグインからも依存されていない
     * （トップレベルと推定される）プラグインに到達するまでの経路をすべて収集する。
     *
     * @param pluginName プラグイン名
     * @return 成功時は `root -> ... -> pluginName` の順で並んだ経路のリスト、失敗時はDependencyError
     */
    override fun getDependencyChains(pluginName: String): Either<DependencyError, List<List<String>>> {
        refreshCacheIfNeeded()

        if (!pluginDataCache.containsKey(pluginName)) {
            return DependencyError.PluginNotFound(pluginName).left()
        }

        // 全プラグインの「直接の逆依存」を1回のパスで事前計算しておく
        // （プラグインごとにgetReverseDependencies相当を呼ぶとO(n^2)の重複走査になるため）
        val reverseIndex = buildReverseDependencyIndex()

        val chains = mutableListOf<List<String>>()

        // pluginNameから逆依存方向へDFSする。訪問済みは経路ごとに追跡し、循環依存を打ち切る
        fun walk(
            current: String,
            pathFromCurrent: List<String>,
            visited: Set<String>
        ) {
            // 同一経路内で既に訪問済みの依存元は循環依存としてあらかじめ除外しておく
            // （除外を先に行わないと、「全ての依存元が循環でスキップされた」ケースで
            //   分岐先0件のままループが終わり、経路が一切記録されずに消失してしまう）
            val unvisitedDependents = reverseIndex[current].orEmpty().filterNot { it in visited }
            if (unvisitedDependents.isEmpty()) {
                // これ以上遡れる（循環でない）依存元が無い = トップレベルまたは循環境界。経路を確定する
                chains.add(pathFromCurrent)
                return
            }
            for (dependent in unvisitedDependents) {
                walk(dependent, listOf(dependent) + pathFromCurrent, visited + dependent)
            }
        }

        walk(pluginName, listOf(pluginName), setOf(pluginName))

        return chains.right()
    }

    /**
     * 全プラグインの「直接の逆依存」インデックスを構築する
     *
     * キー: 依存されているプラグイン名、値: それに依存しているプラグイン名のリスト
     * 必須依存（depend）とオプション依存（softDepend）の両方を対象とする。
     */
    private fun buildReverseDependencyIndex(): Map<String, List<String>> {
        val index = mutableMapOf<String, MutableList<String>>()
        for ((name, data) in pluginDataCache) {
            val info = extractDependencyInfo(name, data)
            for (dep in info.depend + info.softDepend) {
                // 自己依存は無視する（getReverseDependenciesと同様の挙動に揃える）
                if (dep == name) continue
                index.getOrPut(dep) { mutableListOf() }.add(name)
            }
        }
        return index
    }

    /**
     * すべてのインストール済みプラグインの依存関係情報を取得する
     *
     * @return プラグイン名とDependencyInfoのマップ
     */
    override fun getAllDependencyInfo(): Map<String, DependencyInfo> {
        refreshCacheIfNeeded()

        return pluginDataCache.mapValues { (name, data) ->
            extractDependencyInfo(name, data)
        }
    }

    /**
     * キャッシュが古い場合は更新する
     */
    private fun refreshCacheIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastCacheUpdate > cacheExpirationMs) {
            refreshCache()
        }
    }

    /**
     * プラグインデータのキャッシュを更新する
     */
    private fun refreshCache() {
        pluginDataCache.clear()

        val pluginsDir = pluginDirectory.getPluginsDirectory()
        val jarFiles =
            pluginsDir.listFiles { file ->
                file.isFile && file.extension == "jar"
            } ?: return

        for (jarFile in jarFiles) {
            try {
                val pluginData = PluginDataUtils.getPluginData(jarFile) ?: continue
                val name = extractPluginName(pluginData)
                if (name.isNotEmpty()) {
                    pluginDataCache[name] = pluginData
                }
            } catch (e: Exception) {
                // 読み込み失敗したjarはスキップ
            }
        }

        lastCacheUpdate = System.currentTimeMillis()
    }

    /**
     * PluginDataからプラグイン名を抽出する
     */
    private fun extractPluginName(pluginData: PluginData): String =
        when (pluginData) {
            is PluginData.BukkitPluginData -> pluginData.name
            is PluginData.PaperPluginData -> pluginData.name
        }

    /**
     * PluginDataからDependencyInfoを抽出する
     */
    private fun extractDependencyInfo(
        pluginName: String,
        pluginData: PluginData
    ): DependencyInfo =
        when (pluginData) {
            is PluginData.BukkitPluginData ->
                DependencyInfo(
                    pluginName = pluginName,
                    depend = pluginData.depend,
                    softDepend = pluginData.softDepend,
                    loadBefore = pluginData.loadBefore
                )
            is PluginData.PaperPluginData ->
                DependencyInfo(
                    pluginName = pluginName,
                    depend = pluginData.depend,
                    softDepend = pluginData.softDepend,
                    loadBefore = pluginData.loadBefore
                )
        }

    /**
     * 依存関係ツリーのノードを構築する（再帰的）
     */
    private fun buildNode(
        pluginName: String,
        pluginData: PluginData?,
        includeSoftDependencies: Boolean,
        visited: MutableSet<String>,
        isRequired: Boolean
    ): DependencyNode {
        val isInstalled = pluginData != null

        // 循環依存を防ぐ
        if (visited.contains(pluginName)) {
            return DependencyNode(
                pluginName = pluginName,
                isInstalled = isInstalled,
                isRequired = isRequired,
                children = emptyList()
            )
        }
        visited.add(pluginName)

        // 子ノードを構築
        val children = mutableListOf<DependencyNode>()

        if (pluginData != null) {
            val info = extractDependencyInfo(pluginName, pluginData)

            // 必須依存を追加
            for (dep in info.depend) {
                val depData = pluginDataCache[dep]
                children.add(
                    buildNode(dep, depData, includeSoftDependencies, visited.toMutableSet(), isRequired = true)
                )
            }

            // オプション依存を追加（フラグが有効な場合）
            if (includeSoftDependencies) {
                for (dep in info.softDepend) {
                    val depData = pluginDataCache[dep]
                    children.add(
                        buildNode(dep, depData, includeSoftDependencies, visited.toMutableSet(), isRequired = false)
                    )
                }
            }
        }

        return DependencyNode(
            pluginName = pluginName,
            isInstalled = isInstalled,
            isRequired = isRequired,
            children = children
        )
    }

    /**
     * ツリーから不足している依存を収集する（再帰的）
     */
    private fun collectMissingDependencies(
        node: DependencyNode,
        missingRequired: MutableList<String>,
        missingSoft: MutableList<String>
    ) {
        if (!node.isInstalled) {
            if (node.isRequired) {
                missingRequired.add(node.pluginName)
            } else {
                missingSoft.add(node.pluginName)
            }
        }

        for (child in node.children) {
            collectMissingDependencies(child, missingRequired, missingSoft)
        }
    }
}