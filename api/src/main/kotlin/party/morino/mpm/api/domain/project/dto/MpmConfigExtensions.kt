/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related
 * and neighboring rights to this software to the public domain worldwide.
 * This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.project.dto

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import party.morino.mpm.api.domain.plugin.model.VersionSpecifierParser

/**
 * MpmConfigに対するextension functions
 */

/**
 * pluginsマップをキー（プラグイン名）でアルファベット順（a-Z）にソートした新しいMpmConfigを返す
 * 大文字小文字を区別せずにソートを行う
 *
 * @return pluginsがソートされた新しいMpmConfig
 */
fun MpmConfig.withSortedPlugins(): MpmConfig {
    // キーを大文字小文字を区別せずにソート（a-Z順）
    val sortedPlugins = plugins.toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    // ソート済みのpluginsマップを持つ新しいMpmConfigを返す
    return this.copy(plugins = sortedPlugins)
}

/**
 * Sync依存関係のバリデーションを行う
 *
 * 以下のエラーをチェックする:
 * - ターゲットプラグインが存在しない
 * - ターゲットがunmanagedである
 * - 循環依存がある（自己参照を含む）
 *
 * `A <- sync:A の B <- sync:B の C` のような多段 sync は**エラーにしない**。
 * 更新経路（[getSyncDescendants] のBFS）は多段でも親から順に追従を伝播できるため、
 * ここで拒否すると `mpm install` だけが失敗して更新経路と挙動が食い違ってしまう。
 * 多段が破綻するのは循環している場合だけなので、その判定は
 * [detectCircularDependencies] に一本化している。
 *
 * @return 成功時はUnit、失敗時はSyncDependencyError
 */
fun MpmConfig.validateSyncDependencies(): Either<SyncDependencyError, Unit> {
    // 各プラグインのSync依存関係をチェック
    for ((pluginName, versionString) in plugins) {
        // ターゲットプラグインを取得（Sync形式でない場合はスキップ）
        val targetPlugin = VersionSpecifierParser.extractSyncTarget(versionString) ?: continue

        // ターゲットプラグインのバージョンを取得（存在しない場合はエラー）
        val targetVersion =
            plugins[targetPlugin]
                ?: return SyncDependencyError.TargetNotFound(pluginName, targetPlugin).left()

        // ターゲットがunmanagedの場合はエラー（追従すべきバージョンが管理されていない）
        if (targetVersion == "unmanaged") {
            return SyncDependencyError.TargetIsUnmanaged(pluginName, targetPlugin).left()
        }
    }

    // 循環依存をチェック
    detectCircularDependencies()?.let { return SyncDependencyError.CircularDependency(it).left() }

    return Unit.right()
}

/**
 * Sync依存関係における循環依存を検出する
 *
 * DFS（深さ優先探索）を使用して循環を検出する。
 * 多段 sync が許可されたため、`A -> B -> A` だけでなく `A -> A`（自己参照）も
 * ここで唯一検出される（探索パスに再訪した時点で循環と判定するため）。
 *
 * @return 循環が見つかった場合は循環を構成するプラグイン名のリスト、見つからない場合はnull
 */
fun MpmConfig.detectCircularDependencies(): List<String>? {
    // Sync依存関係のグラフを構築
    val syncGraph = mutableMapOf<String, String>()
    for ((pluginName, versionString) in plugins) {
        VersionSpecifierParser.extractSyncTarget(versionString)?.let { target ->
            syncGraph[pluginName] = target
        }
    }

    // 訪問済みセット
    val visited = mutableSetOf<String>()
    // 現在の探索パス
    val path = mutableListOf<String>()
    // パスに含まれるかどうかのセット（高速ルックアップ用）
    val inPath = mutableSetOf<String>()

    // DFSで循環を検出
    fun dfs(node: String): List<String>? {
        // 既に探索パスに含まれている場合、循環を検出
        if (node in inPath) {
            // 循環の開始点を見つけてパスを返す
            val cycleStart = path.indexOf(node)
            return path.subList(cycleStart, path.size) + node
        }

        // 既に訪問済みの場合はスキップ
        if (node in visited) {
            return null
        }

        // 訪問済みとしてマーク
        visited.add(node)
        path.add(node)
        inPath.add(node)

        // 隣接ノード（依存先）を探索
        syncGraph[node]?.let { neighbor ->
            dfs(neighbor)?.let { cycle ->
                return cycle
            }
        }

        // パスから削除
        path.removeLast()
        inPath.remove(node)

        return null
    }

    // すべてのSync依存を持つプラグインから探索開始
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
 * これにより、インストール時に正しい順序でプラグインを処理できる。
 * `A <- sync:A の B <- sync:B の C` のような多段 sync でも、
 * 入次数が親の処理後に0になるため 親 -> 子 -> 孫 の順に並ぶ。
 *
 * @return ソートされたプラグイン名のリスト
 */
fun MpmConfig.topologicalSortPlugins(): List<String> {
    // Sync依存関係のグラフを構築（逆方向: 依存先 → 依存元）
    val dependents = mutableMapOf<String, MutableList<String>>()
    // 各ノードの入次数（依存している数）
    val inDegree = mutableMapOf<String, Int>()

    // 全プラグインを初期化
    for (pluginName in plugins.keys) {
        inDegree[pluginName] = 0
        dependents[pluginName] = mutableListOf()
    }

    // Sync依存関係を構築
    for ((pluginName, versionString) in plugins) {
        VersionSpecifierParser.extractSyncTarget(versionString)?.let { target ->
            if (plugins.containsKey(target)) {
                // target → pluginName の辺を追加
                dependents.getOrPut(target) { mutableListOf() }.add(pluginName)
                // pluginNameの入次数を増加
                inDegree[pluginName] = inDegree.getOrDefault(pluginName, 0) + 1
            }
        }
    }

    // 入次数が0のノードをキューに追加
    val queue = ArrayDeque<String>()
    for ((pluginName, degree) in inDegree) {
        if (degree == 0) {
            queue.add(pluginName)
        }
    }

    // トポロジカルソートを実行
    val result = mutableListOf<String>()
    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        result.add(current)

        // 現在のノードに依存しているノードの入次数を減少
        for (dependent in dependents[current] ?: emptyList()) {
            val newDegree = inDegree.getOrDefault(dependent, 1) - 1
            inDegree[dependent] = newDegree
            if (newDegree == 0) {
                queue.add(dependent)
            }
        }
    }

    // 循環依存がある場合、一部のノードがresultに含まれない可能性がある
    // その場合は残りのノードを追加（通常はvalidateSyncDependenciesで先にエラーになる）
    val remaining = plugins.keys.filter { it !in result }
    result.addAll(remaining)

    return result
}

/**
 * Sync依存関係を持つプラグインの一覧を取得する
 *
 * @return Sync依存を持つプラグイン名とそのターゲットのマップ
 */
fun MpmConfig.getSyncDependencies(): Map<String, String> =
    plugins
        .mapNotNull { (name, version) ->
            VersionSpecifierParser.extractSyncTarget(version)?.let { name to it }
        }.toMap()

/**
 * 指定されたプラグインに同期しているプラグインの一覧を取得する
 *
 * @param targetPluginName ターゲットプラグイン名
 * @return 指定されたプラグインに同期しているプラグイン名のリスト
 */
fun MpmConfig.getPluginsSyncingTo(targetPluginName: String): List<String> =
    plugins
        .filter { (_, versionString) ->
            VersionSpecifierParser.extractSyncTarget(versionString) == targetPluginName
        }.keys
        .toList()

/**
 * 指定されたプラグインに直接・間接的に同期しているプラグインをすべて取得する
 *
 * `A <- sync:A の B <- sync:B の C` のような多段 sync でも、
 * 親から近い順（幅優先＝トポロジカル順）に列挙するため、
 * この順序で追従更新すれば孫まで正しく伝播する。
 *
 * mpm.json を手編集すると多段 sync や循環 sync が実在しうるため、
 * 訪問済み集合で重複と無限ループの双方を防いでいる。
 *
 * @param targetPluginName 起点となるプラグイン名
 * @return 追従更新すべきプラグイン名のリスト（親に近い順）
 */
fun MpmConfig.getSyncDescendants(targetPluginName: String): List<String> {
    // 追従対象の集合（挿入順＝親に近い順を保つ）
    val descendants = LinkedHashSet<String>()
    val queue = ArrayDeque<String>()
    queue.add(targetPluginName)

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        for (child in getPluginsSyncingTo(current)) {
            // 循環 sync（起点に戻る場合を含む）でも無限ループにならないよう、既出の子は辿らない
            if (child == targetPluginName || !descendants.add(child)) continue
            queue.add(child)
        }
    }

    return descendants.toList()
}