/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.repository

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.domain.repository.PluginRepositorySource
import party.morino.mpm.api.domain.repository.RepositoryConfig
import party.morino.mpm.api.domain.repository.RepositoryFile
import party.morino.mpm.infrastructure.repository.RepositoryManagerImpl

/**
 * RepositoryManagerImpl.resolvePluginNames の照合（ファイル名 / id / aliases）を検証する
 */
@DisplayName("RepositoryManager.resolvePluginNames")
class ResolvePluginNameTest {
    /**
     * "QuickShop" という1件のリポジトリファイル（aliasに"QuickShop-Hikari"を持つ）を返すテスト用ソース
     */
    private class FakeSource : PluginRepositorySource {
        private val file =
            RepositoryFile(
                id = "QuickShop",
                repositories = listOf(RepositoryConfig(type = "hangar", repositoryId = "Ghost-chu/QuickShop-Hikari")),
                aliases = listOf("QuickShop-Hikari")
            )

        override suspend fun isAvailable(): Boolean = true

        override suspend fun getAvailablePlugins(): List<String> = listOf("QuickShop")

        override suspend fun getRepositoryFile(pluginName: String): RepositoryFile? =
            if (pluginName.equals("QuickShop", ignoreCase = true)) file else null

        override fun getSourceType(): String = "fake"

        override fun getIdentifier(): String = "fake"
    }

    private val manager = RepositoryManagerImpl(listOf(FakeSource()), { listOf(FakeSource()) })

    @Test
    @DisplayName("matches by repository file name (case-insensitive)")
    fun matchesByFileName() =
        runBlocking {
            val resolved = manager.resolvePluginNames(listOf("QuickShop", "quickshop"))
            assertEquals("QuickShop", resolved["QuickShop"])
            assertEquals("QuickShop", resolved["quickshop"])
        }

    @Test
    @DisplayName("matches a differently-named plugin.yml via aliases")
    fun matchesByAlias() =
        runBlocking {
            // plugin.ymlのnameが "QuickShop-Hikari" でも "QuickShop" に解決される
            val resolved = manager.resolvePluginNames(listOf("QuickShop-Hikari", "quickshop-hikari"))
            assertEquals("QuickShop", resolved["QuickShop-Hikari"])
            assertEquals("QuickShop", resolved["quickshop-hikari"])
        }

    @Test
    @DisplayName("omits names that do not match")
    fun omitsUnmatched() =
        runBlocking {
            val resolved = manager.resolvePluginNames(listOf("QuickShop", "SomeOtherPlugin"))
            assertEquals("QuickShop", resolved["QuickShop"])
            assertFalse(resolved.containsKey("SomeOtherPlugin"))
        }
}