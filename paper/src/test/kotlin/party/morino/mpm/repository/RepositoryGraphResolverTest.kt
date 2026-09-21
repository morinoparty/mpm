/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.repository

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.infrastructure.repository.graph.RepositoryGraphResolver
import party.morino.mpm.infrastructure.repository.graph.RepositoryIndexFetcher

class RepositoryGraphResolverTest {
    /**
     * プラグイン定義1件分のJSONを組み立てる
     */
    private fun plugin(
        id: String,
        type: String = "github",
        repoId: String = "morinoparty/MineAuth"
    ): String = """"$id": { "id": "$id", "repositories": [{ "type": "$type", "id": "$repoId" }] }"""

    /**
     * リンク1件分のJSONを組み立てる
     */
    private fun link(
        url: String,
        sources: List<String> = emptyList()
    ): String {
        val sourcesJson = sources.joinToString(",") { "\"$it\"" }
        return """{ "index": "$url", "allowedSources": [$sourcesJson] }"""
    }

    /**
     * インデックス1件分のJSONを組み立てる
     */
    private fun index(
        plugins: List<String>,
        children: List<String> = emptyList()
    ): String =
        """{ "schemaVersion": 1, "plugins": { ${plugins.joinToString(
            ","
        )} }, "children": [${children.joinToString(",")}] }"""

    /**
     * URL -> 本文 の対応表から fetcher を作る
     */
    private fun fetcher(responses: Map<String, String>) = RepositoryIndexFetcher { responses[it] }

    @Test
    @DisplayName("parent definition wins over child definition")
    fun parentWins() {
        val root = "https://root.example/index.json"
        val child = "https://child.example/index.json"
        val responses =
            mapOf(
                root to index(listOf(plugin("MineAuth", repoId = "morinoparty/Central")), listOf(link(child))),
                child to index(listOf(plugin("MineAuth"), plugin("MineAuth-addon-a")))
            )

        val graph = runBlocking { RepositoryGraphResolver(fetcher(responses)).resolve(root) }

        assertNotNull(graph)
        assertEquals("morinoparty/Central", graph!!.plugins["MineAuth"]!!.repositories[0].repositoryId)
        assertTrue("MineAuth-addon-a" in graph.plugins)
    }

    @Test
    @DisplayName("children may define any plugin name when no allowedSources is given")
    fun unrestrictedChain() {
        val root = "https://root.example/index.json"
        val child = "https://child.example/index.json"
        val grandchild = "https://grandchild.example/index.json"
        val responses =
            mapOf(
                root to index(emptyList(), listOf(link(child))),
                child to index(listOf(plugin("MineAuth")), listOf(link(grandchild))),
                grandchild to index(listOf(plugin("Vault", repoId = "MilkBowl/Vault")))
            )

        val graph = runBlocking { RepositoryGraphResolver(fetcher(responses)).resolve(root) }

        assertEquals(setOf("MineAuth", "Vault"), graph!!.plugins.keys)
    }

    @Test
    @DisplayName("allowedSources of every link on the path apply to deeper nodes")
    fun allowedSourcesAccumulateAlongPath() {
        val root = "https://root.example/index.json"
        val child = "https://child.example/index.json"
        val grandchild = "https://grandchild.example/index.json"
        val responses =
            mapOf(
                root to index(emptyList(), listOf(link(child, sources = listOf("github:morinoparty/*")))),
                // 子は孫に無制限のリンクを張るが、ルートの allowedSources が残る
                child to index(emptyList(), listOf(link(grandchild))),
                grandchild to index(listOf(plugin("MineAuth-addon-a"), plugin("Vault", repoId = "MilkBowl/Vault")))
            )

        val graph = runBlocking { RepositoryGraphResolver(fetcher(responses)).resolve(root) }

        assertEquals(setOf("MineAuth-addon-a"), graph!!.plugins.keys)
    }

    @Test
    @DisplayName("nodes beyond maxDepth are not fetched")
    fun depthLimit() {
        val urls = (0..3).map { "https://node$it.example/index.json" }
        val responses =
            urls
                .mapIndexed { i, url ->
                    val children = if (i < urls.lastIndex) listOf(link(urls[i + 1])) else emptyList()
                    url to index(listOf(plugin("MineAuth-addon-$i")), children)
                }.toMap()

        val graph = runBlocking { RepositoryGraphResolver(fetcher(responses), maxDepth = 2).resolve(urls[0]) }

        // 深さ0,1,2 のノードだけ読まれ、深さ3は読まれない
        assertEquals(urls.take(3), graph!!.visitedUrls)
        assertNull(graph.plugins["MineAuth-addon-3"])
        assertTrue(graph.warnings.any { "深さの上限" in it })
    }

    @Test
    @DisplayName("cyclic links are visited only once")
    fun cycleDetection() {
        val a = "https://a.example/index.json"
        val b = "https://b.example/index.json"
        val responses =
            mapOf(
                a to index(listOf(plugin("MineAuth")), listOf(link(b))),
                b to index(listOf(plugin("MineAuth-addon-b")), listOf(link(a)))
            )

        val graph = runBlocking { RepositoryGraphResolver(fetcher(responses)).resolve(a) }

        assertEquals(listOf(a, b), graph!!.visitedUrls)
        assertEquals(setOf("MineAuth", "MineAuth-addon-b"), graph.plugins.keys)
    }

    @Test
    @DisplayName("unsafe or unreachable children are skipped without failing the root")
    fun childFailuresAreNonFatal() {
        val root = "https://root.example/index.json"
        val dead = "https://dead.example/index.json"
        val responses =
            mapOf(
                root to
                    index(
                        listOf(plugin("Vault", repoId = "MilkBowl/Vault")),
                        listOf(
                            link(dead),
                            link("http://insecure.example/index.json"),
                            link("https://127.0.0.1/index.json")
                        )
                    )
            )

        val graph = runBlocking { RepositoryGraphResolver(fetcher(responses)).resolve(root) }

        assertNotNull(graph)
        assertEquals(setOf("Vault"), graph!!.plugins.keys)
        // 到達不能な子1件は取得を試み、安全でない2件は試みない
        assertEquals(listOf(root, dead), graph.visitedUrls)
        assertEquals(3, graph.warnings.size)
    }

    @Test
    @DisplayName("resolve returns null when the root is unreachable")
    fun rootFailure() {
        val graph =
            runBlocking { RepositoryGraphResolver(fetcher(emptyMap())).resolve("https://root.example/index.json") }
        assertNull(graph)
    }
}