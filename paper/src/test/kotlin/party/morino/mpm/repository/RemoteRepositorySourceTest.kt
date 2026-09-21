/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.repository

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import party.morino.mpm.MpmTest
import party.morino.mpm.infrastructure.repository.RemoteRepositorySource
import party.morino.mpm.utils.MockDataLoader

@ExtendWith(MpmTest::class)
class RemoteRepositorySourceTest {
    // テスト用のベースURL（設定値はディレクトリ形式で、index.json が補われる）
    private val baseUrl = "https://example.com/repository"

    // 子リポジトリのインデックスURL
    private val childUrl = "https://child.example.org/mpm/index.json"

    // 子リポジトリのインデックス（MineAuth と MineAuth-addon-* を定義する）
    private val childIndex =
        """
        {
            "schemaVersion": 1,
            "plugins": {
                "MineAuth": {
                    "id": "MineAuth",
                    "repositories": [{ "type": "github", "id": "morinoparty/MineAuth" }]
                },
                "MineAuth-addon-x": {
                    "id": "MineAuth-addon-x",
                    "repositories": [{ "type": "github", "id": "morinoparty/MineAuth", "fileNameTemplate": "../evil.jar" }]
                },
                "LuckPerms": {
                    "id": "LuckPerms",
                    "repositories": [{ "type": "github", "id": "attacker/LuckPerms" }]
                }
            }
        }
        """.trimIndent()

    // 子リポジトリへのリンクを持つルートのインデックス
    private val rootIndexWithChild =
        """
        {
            "schemaVersion": 1,
            "plugins": {
                "Vault": { "id": "Vault", "repositories": [{ "type": "github", "id": "MilkBowl/Vault" }] }
            },
            "children": [
                {
                    "index": "$childUrl",
                    "scope": ["MineAuth", "MineAuth-*"],
                    "allowedSources": ["github:morinoparty/MineAuth"]
                }
            ]
        }
        """.trimIndent()

    @Test
    @DisplayName("isAvailable should return true when index.json is served")
    fun testIsAvailableSuccess() {
        val source = createTestSource(routes(mapOf("$baseUrl/index.json" to MockDataLoader.Repository.getIndex())))

        runBlocking {
            assertTrue(source.isAvailable())
        }
    }

    @Test
    @DisplayName("isAvailable should return false when server responds with error")
    fun testIsAvailableFailure() {
        val mockEngine =
            MockEngine {
                respond(content = ByteReadChannel(""), status = HttpStatusCode.InternalServerError)
            }
        val source = createTestSource(mockEngine)

        runBlocking {
            assertFalse(source.isAvailable())
        }
    }

    @Test
    @DisplayName("getAvailablePlugins should return plugin list from index.json")
    fun testGetAvailablePluginsSuccess() {
        val source = createTestSource(routes(mapOf("$baseUrl/index.json" to MockDataLoader.Repository.getIndex())))

        runBlocking {
            val plugins = source.getAvailablePlugins()

            // インデックスに定義された3件がソート済みで返ることを確認
            assertEquals(listOf("essentialsx", "luckperms", "worldedit"), plugins)
        }
    }

    @Test
    @DisplayName("getAvailablePlugins should return empty list when index.json does not exist")
    fun testGetAvailablePluginsNotFound() {
        val mockEngine =
            MockEngine {
                respond(content = ByteReadChannel(""), status = HttpStatusCode.NotFound)
            }
        val source = createTestSource(mockEngine)

        runBlocking {
            assertTrue(source.getAvailablePlugins().isEmpty())
        }
    }

    @Test
    @DisplayName("getRepositoryFile should return repository file defined in index.json")
    fun testGetRepositoryFileSuccess() {
        val source = createTestSource(routes(mapOf("$baseUrl/index.json" to MockDataLoader.Repository.getIndex())))

        runBlocking {
            val repositoryFile = source.getRepositoryFile("luckperms")

            assertNotNull(repositoryFile)
            assertEquals("luckperms", repositoryFile!!.id)
            assertEquals("https://luckperms.net", repositoryFile.website)
            assertEquals("https://github.com/LuckPerms/LuckPerms", repositoryFile.source)
            assertEquals("MIT", repositoryFile.license)
            assertEquals(1, repositoryFile.repositories.size)
            assertEquals("modrinth", repositoryFile.repositories[0].type)
        }
    }

    @Test
    @DisplayName("getRepositoryFile should return null when plugin is not defined")
    fun testGetRepositoryFileNotFound() {
        val source = createTestSource(routes(mapOf("$baseUrl/index.json" to MockDataLoader.Repository.getIndex())))

        runBlocking {
            assertNull(source.getRepositoryFile("nonexistent"))
        }
    }

    @Test
    @DisplayName("child repositories should be merged within link constraints")
    fun testChildRepositoryMerged() {
        val source =
            createTestSource(
                routes(mapOf("$baseUrl/index.json" to rootIndexWithChild, childUrl to childIndex))
            )

        runBlocking {
            val plugins = source.getAvailablePlugins()

            // scope 内の MineAuth / MineAuth-addon-x は採用され、scope 外の LuckPerms は捨てられる
            assertEquals(listOf("MineAuth", "MineAuth-addon-x", "Vault"), plugins)

            // 子由来の定義からは fileNameTemplate が落とされている
            val addon = source.getRepositoryFile("MineAuth-addon-x")
            assertNotNull(addon)
            assertNull(addon!!.repositories[0].fileNameTemplate)
        }
    }

    @Test
    @DisplayName("custom headers should be sent only to the root index")
    fun testHeadersNotForwardedToChildren() {
        val seenAuth = mutableMapOf<String, String?>()
        val mockEngine =
            MockEngine { request ->
                val url = request.url.toString()
                seenAuth[url] = request.headers["Authorization"]
                when (url) {
                    "$baseUrl/index.json" -> respondJson(rootIndexWithChild)
                    childUrl -> respondJson(childIndex)
                    else -> respond(content = ByteReadChannel(""), status = HttpStatusCode.NotFound)
                }
            }
        val source = createTestSource(mockEngine, headers = mapOf("Authorization" to "Bearer secret"))

        runBlocking {
            source.getAvailablePlugins()

            assertEquals("Bearer secret", seenAuth["$baseUrl/index.json"])
            assertNull(seenAuth[childUrl])
        }
    }

    @Test
    @DisplayName("getSourceType should return 'remote'")
    fun testGetSourceType() {
        val source = RemoteRepositorySource(baseUrl)
        assertEquals("remote", source.getSourceType())
    }

    @Test
    @DisplayName("getIdentifier should return configured URL")
    fun testGetIdentifier() {
        val source = RemoteRepositorySource(baseUrl)
        assertEquals(baseUrl, source.getIdentifier())
    }

    /**
     * URL -> レスポンス本文 の対応表から MockEngine を作る（未登録のURLは404）
     */
    private fun routes(responses: Map<String, String>): MockEngine =
        MockEngine { request ->
            val body = responses[request.url.toString()]
            if (body != null) {
                respondJson(body)
            } else {
                respond(content = ByteReadChannel(""), status = HttpStatusCode.NotFound)
            }
        }

    /**
     * JSON本文を200で返す
     */
    private fun MockRequestHandleScope.respondJson(body: String) =
        respond(
            content = ByteReadChannel(body),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )

    /**
     * テスト用のRemoteRepositorySourceを作成
     * リフレクションを使用してhttpClientを差し替える
     */
    private fun createTestSource(
        mockEngine: MockEngine,
        headers: Map<String, String> = emptyMap()
    ): RemoteRepositorySource {
        val source = RemoteRepositorySource(baseUrl, headers)

        // リフレクションを使用してhttpClientフィールドにアクセス
        val httpClientField = RemoteRepositorySource::class.java.getDeclaredField("httpClient")
        httpClientField.isAccessible = true
        httpClientField.set(source, HttpClient(mockEngine))

        return source
    }
}