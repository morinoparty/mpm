/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
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
import party.morino.mpm.MinecraftPluginManagerTest
import party.morino.mpm.core.repository.RemoteRepositorySource
import party.morino.mpm.utils.MockDataLoader

@ExtendWith(MinecraftPluginManagerTest::class)
class RemoteRepositorySourceTest {
    // テスト用のベースURL
    private val baseUrl = "https://example.com/repository"

    @Test
    @DisplayName("isAvailable should return true when server responds successfully")
    fun testIsAvailableSuccess() {
        // モックエンジンを作成
        val mockEngine =
            MockEngine { request ->
                // HEADリクエストに対して200 OKを返す
                respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "text/html")
                )
            }

        // モックエンジンを使用するRemoteRepositorySourceを作成
        val source = createTestSource(mockEngine)

        // テストを実行
        runBlocking {
            val isAvailable = source.isAvailable()
            assertTrue(isAvailable)
        }
    }

    @Test
    @DisplayName("isAvailable should return false when server is not reachable")
    fun testIsAvailableFailure() {
        // モックエンジンを作成（エラーをスロー）
        val mockEngine =
            MockEngine { request ->
                // サーバーエラーを返す
                respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode.InternalServerError
                )
            }

        // モックエンジンを使用するRemoteRepositorySourceを作成
        val source = createTestSource(mockEngine)

        // テストを実行
        runBlocking {
            val isAvailable = source.isAvailable()
            assertFalse(isAvailable)
        }
    }

    @Test
    @DisplayName("getAvailablePlugins should return plugin list when index.json exists")
    fun testGetAvailablePluginsSuccess() {
        // モックエンジンを作成
        val mockEngine =
            MockEngine { request ->
                // _list.jsonへのリクエストに対してモックデータを返す
                when (request.url.toString()) {
                    "$baseUrl/list" -> {
                        respond(
                            content = ByteReadChannel(MockDataLoader.Repository.getIndex()),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    else -> {
                        respond(
                            content = ByteReadChannel(""),
                            status = HttpStatusCode.NotFound
                        )
                    }
                }
            }

        // モックエンジンを使用するRemoteRepositorySourceを作成
        val source = createTestSource(mockEngine)

        // テストを実行
        runBlocking {
            val plugins = source.getAvailablePlugins()

            // プラグイン一覧が正しく取得できることを確認
            assertEquals(3, plugins.size)
            assertEquals("luckperms", plugins[0])
            assertEquals("essentialsx", plugins[1])
            assertEquals("worldedit", plugins[2])
        }
    }

    @Test
    @DisplayName("getAvailablePlugins should return empty list when index.json does not exist")
    fun testGetAvailablePluginsNotFound() {
        // モックエンジンを作成
        val mockEngine =
            MockEngine { request ->
                // 404 Not Foundを返す
                respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode.NotFound
                )
            }

        // モックエンジンを使用するRemoteRepositorySourceを作成
        val source = createTestSource(mockEngine)

        // テストを実行
        runBlocking {
            val plugins = source.getAvailablePlugins()

            // 空のリストが返されることを確認
            assertTrue(plugins.isEmpty())
        }
    }

    @Test
    @DisplayName("getRepositoryFile should return repository file when it exists")
    fun testGetRepositoryFileSuccess() {
        // モックエンジンを作成
        val mockEngine =
            MockEngine { request ->
                // luckperms.jsonへのリクエストに対してモックデータを返す
                when (request.url.toString()) {
                    "$baseUrl/plugins/luckperms.json" -> {
                        respond(
                            content = ByteReadChannel(MockDataLoader.Repository.getLuckPermsRepository()),
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    else -> {
                        respond(
                            content = ByteReadChannel(""),
                            status = HttpStatusCode.NotFound
                        )
                    }
                }
            }

        // モックエンジンを使用するRemoteRepositorySourceを作成
        val source = createTestSource(mockEngine)

        // テストを実行
        runBlocking {
            val repositoryFile = source.getRepositoryFile("luckperms")

            // リポジトリファイルが正しく取得できることを確認
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
    @DisplayName("getRepositoryFile should return null when file does not exist")
    fun testGetRepositoryFileNotFound() {
        // モックエンジンを作成
        val mockEngine =
            MockEngine { request ->
                // 404 Not Foundを返す
                respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode.NotFound
                )
            }

        // モックエンジンを使用するRemoteRepositorySourceを作成
        val source = createTestSource(mockEngine)

        // テストを実行
        runBlocking {
            val repositoryFile = source.getRepositoryFile("nonexistent")

            // nullが返されることを確認
            assertNull(repositoryFile)
        }
    }

    @Test
    @DisplayName("getSourceType should return 'remote'")
    fun testGetSourceType() {
        val source = RemoteRepositorySource(baseUrl)
        assertEquals("remote", source.getSourceType())
    }

    @Test
    @DisplayName("getIdentifier should return base URL")
    fun testGetIdentifier() {
        val source = RemoteRepositorySource(baseUrl)
        assertEquals(baseUrl, source.getIdentifier())
    }

    /**
     * テスト用のRemoteRepositorySourceを作成
     * リフレクションを使用してhttpClientを差し替える
     */
    private fun createTestSource(mockEngine: MockEngine): RemoteRepositorySource {
        val source = RemoteRepositorySource(baseUrl)

        // リフレクションを使用してhttpClientフィールドにアクセス
        val httpClientField = RemoteRepositorySource::class.java.getDeclaredField("httpClient")
        httpClientField.isAccessible = true
        httpClientField.set(source, HttpClient(mockEngine))

        return source
    }
}