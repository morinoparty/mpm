/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.repository.graph

import kotlinx.serialization.json.Json
import party.morino.mpm.api.domain.repository.RepositoryFile
import party.morino.mpm.api.domain.repository.model.RepositoryIndex
import party.morino.mpm.api.domain.repository.model.RepositoryLink

/**
 * ルートのインデックスから子リポジトリを再帰的にたどり、プラグインカタログを組み立てる
 *
 * 探索規則:
 * - 幅優先で探索する。ルートを深さ0とし、深さ [maxDepth] のノードまで読み取る
 *   （それより先の children は無視して警告する）
 * - 同じURL（正規化後）は一度しか取得しない（循環参照・重複リンク対策）
 * - 取得するノード数の上限を [maxNodes] で抑える（深さ5×大きな分岐でのフェッチ爆発を防ぐ）
 * - 同名のプラグインは先に見つかった側（祖先側・浅い側）が勝つ
 * - 子が定義できる名前に制限は無い（チェーンのようにリポジトリをつなげる）。
 *   経路上の [RepositoryLink] に allowedSources があれば深さ1以上の定義に適用し、
 *   さらに [RepositoryLinkPolicy.stripUntrustedFields] で危険なフィールドを落とす
 * - 子の取得失敗は警告に留めて続行する。ルートの取得失敗は探索全体の失敗（null）とする
 *
 * HTTPは [RepositoryIndexFetcher] に委ねているため、このクラス自体は外部依存を持たない。
 *
 * @property fetcher インデックスの生テキストを取得する関数
 * @property maxDepth 読み取る最大の深さ（ルート=0）
 * @property maxNodes 取得するインデックスの最大数
 */
class RepositoryGraphResolver(
    private val fetcher: RepositoryIndexFetcher,
    private val maxDepth: Int = DEFAULT_MAX_DEPTH,
    private val maxNodes: Int = DEFAULT_MAX_NODES
) {
    // JSONパーサー（未知のキーは無視し、将来のフィールド追加で壊れないようにする）
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        // ルートから数えて読み取る最大の深さ
        const val DEFAULT_MAX_DEPTH = 5

        // 1回の探索で取得するインデックスの上限
        const val DEFAULT_MAX_NODES = 50
    }

    /**
     * 探索キューの要素
     * @property url このノードのインデックスURL
     * @property depth ルートからの深さ
     * @property constraints ルートからこのノードに到達するまでに通ったリンク
     */
    private data class Node(
        val url: String,
        val depth: Int,
        val constraints: List<RepositoryLink>
    )

    /**
     * ルートURLからグラフを探索し、カタログを構築する
     * @param rootUrl ルートのインデックスURL
     * @return 探索結果。ルートが取得・解析できなかった場合はnull
     */
    suspend fun resolve(rootUrl: String): ResolvedRepositoryGraph? {
        val plugins = linkedMapOf<String, RepositoryFile>()
        val visitedUrls = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        // 正規化済みURLの集合。キューに入れた時点で登録し、二重取得を防ぐ
        val seen = mutableSetOf(normalizeUrl(rootUrl))
        val queue = ArrayDeque<Node>()
        queue.addLast(Node(rootUrl, depth = 0, constraints = emptyList()))

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            visitedUrls += node.url

            val index = fetchIndex(node.url, warnings)
            if (index == null) {
                // ルートが読めなければカタログ自体が成立しない
                if (node.depth == 0) return null
                continue
            }

            mergePlugins(node, index, plugins, warnings)
            enqueueChildren(node, index, seen, queue, warnings)
        }

        return ResolvedRepositoryGraph(plugins, visitedUrls, warnings)
    }

    /**
     * インデックスを取得して解析する。失敗は警告として記録しnullを返す
     */
    private suspend fun fetchIndex(
        url: String,
        warnings: MutableList<String>
    ): RepositoryIndex? {
        val body =
            fetcher.fetch(url) ?: run {
                warnings += "インデックスを取得できませんでした: $url"
                return null
            }
        return try {
            json.decodeFromString<RepositoryIndex>(body)
        } catch (e: Exception) {
            warnings += "インデックスの解析に失敗しました: $url (${e.message})"
            null
        }
    }

    /**
     * ノードのプラグイン定義をカタログへ取り込む
     *
     * 祖先側で既に定義されている名前はスキップする（親が勝つ）。
     * 深さ1以上の定義は配布元の制約（allowedSources）を満たすものだけ採用し、危険なフィールドを落とす。
     */
    private fun mergePlugins(
        node: Node,
        index: RepositoryIndex,
        plugins: MutableMap<String, RepositoryFile>,
        warnings: MutableList<String>
    ) {
        for ((name, file) in index.plugins) {
            if (plugins.containsKey(name)) continue

            val accepted =
                if (node.depth == 0) {
                    // ルートは管理者が設定で指定した信頼済みリポジトリ。キーの整合性だけ確認する
                    if (name == file.id && RepositoryLinkPolicy.isValidPluginName(name)) {
                        file
                    } else {
                        warnings += "キーと id が一致しないためスキップしました: $name (${node.url})"
                        continue
                    }
                } else {
                    if (!RepositoryLinkPolicy.isPermitted(name, file, node.constraints)) {
                        warnings += "配布元の制約（allowedSources）に反するためスキップしました: $name (${node.url})"
                        continue
                    }
                    RepositoryLinkPolicy.stripUntrustedFields(file)
                }

            plugins[name] = accepted
        }
    }

    /**
     * ノードの children を検証してキューへ追加する
     */
    private fun enqueueChildren(
        node: Node,
        index: RepositoryIndex,
        seen: MutableSet<String>,
        queue: ArrayDeque<Node>,
        warnings: MutableList<String>
    ) {
        if (index.children.isEmpty()) return

        // 深さの上限に達したノードの children は読まない
        if (node.depth >= maxDepth) {
            warnings += "深さの上限 ($maxDepth) に達したため children を無視しました: ${node.url}"
            return
        }

        for (link in index.children) {
            if (!RepositoryLinkPolicy.isSafeChildUrl(link.index)) {
                warnings += "安全でないリンク先を無視しました: ${link.index} (${node.url})"
                continue
            }
            // 既に訪問予定のURLは再度たどらない（循環・重複）
            if (!seen.add(normalizeUrl(link.index))) continue

            if (seen.size > maxNodes) {
                warnings += "取得するインデックス数の上限 ($maxNodes) に達したためリンクを無視しました: ${link.index}"
                continue
            }

            queue.addLast(Node(link.index, node.depth + 1, node.constraints + link))
        }
    }

    /**
     * 訪問済み判定用にURLを正規化する（前後の空白と末尾スラッシュを除く）
     */
    private fun normalizeUrl(url: String): String = url.trim().trimEnd('/')
}