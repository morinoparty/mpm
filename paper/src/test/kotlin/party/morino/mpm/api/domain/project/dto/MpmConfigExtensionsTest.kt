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

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("MpmConfigExtensionsのテスト")
class MpmConfigExtensionsTest {
    @Test
    @DisplayName("Sorts plugins alphabetically (case-insensitive)")
    fun testWithSortedPlugins() {
        // テストデータ: 逆順に並んだプラグイン
        val unsortedPlugins =
            mapOf(
                "Zebra" to "1.0.0",
                "alpha" to "2.0.0",
                "Beta" to "3.0.0",
                "charlie" to "unmanaged"
            )

        // MpmConfigを作成
        val config =
            MpmConfig(
                name = "test-project",
                version = "1.0.0",
                plugins = unsortedPlugins
            )

        // ソート実行
        val sortedConfig = config.withSortedPlugins()

        // ソート順を確認
        val pluginKeys = sortedConfig.plugins.keys.toList()
        assertEquals(4, pluginKeys.size, "Plugin count should remain the same")

        // アルファベット順（大文字小文字を区別しない）になっているか確認
        assertEquals("alpha", pluginKeys[0], "First plugin should be 'alpha'")
        assertEquals("Beta", pluginKeys[1], "Second plugin should be 'Beta'")
        assertEquals("charlie", pluginKeys[2], "Third plugin should be 'charlie'")
        assertEquals("Zebra", pluginKeys[3], "Fourth plugin should be 'Zebra'")

        // 値も保持されているか確認
        assertEquals("2.0.0", sortedConfig.plugins["alpha"], "alpha version should be preserved")
        assertEquals("3.0.0", sortedConfig.plugins["Beta"], "Beta version should be preserved")
        assertEquals("unmanaged", sortedConfig.plugins["charlie"], "charlie should remain unmanaged")
        assertEquals("1.0.0", sortedConfig.plugins["Zebra"], "Zebra version should be preserved")
    }

    @Test
    @DisplayName("Handles empty plugins map")
    fun testWithSortedPluginsEmpty() {
        // 空のプラグインマップ
        val config =
            MpmConfig(
                name = "test-project",
                version = "1.0.0",
                plugins = emptyMap()
            )

        // ソート実行
        val sortedConfig = config.withSortedPlugins()

        // 空のままであることを確認
        assertEquals(0, sortedConfig.plugins.size, "Plugin map should remain empty")
    }

    @Test
    @DisplayName("Handles single plugin")
    fun testWithSortedPluginsSingle() {
        // 単一のプラグイン
        val config =
            MpmConfig(
                name = "test-project",
                version = "1.0.0",
                plugins = mapOf("OnlyOne" to "1.0.0")
            )

        // ソート実行
        val sortedConfig = config.withSortedPlugins()

        // 単一のプラグインが保持されているか確認
        assertEquals(1, sortedConfig.plugins.size, "Should have one plugin")
        assertEquals("1.0.0", sortedConfig.plugins["OnlyOne"], "Plugin version should be preserved")
    }

    @Nested
    @DisplayName("Sync dependency validation tests")
    inner class ValidateSyncDependenciesTest {
        @Test
        @DisplayName("Valid sync dependencies pass validation")
        fun testValidSyncDependencies() {
            val config =
                MpmConfig(
                    name = "test-project",
                    version = "1.0.0",
                    plugins =
                        mapOf(
                            "QuickShop" to "6.2.0",
                            "QuickShop-Addon" to "sync:QuickShop"
                        )
                )

            val result = config.validateSyncDependencies()
            assertTrue(result.isRight(), "Valid sync dependencies should pass validation")
        }

        @Test
        @DisplayName("Returns error when target plugin not found")
        fun testTargetNotFound() {
            val config =
                MpmConfig(
                    name = "test-project",
                    version = "1.0.0",
                    plugins =
                        mapOf(
                            "QuickShop-Addon" to "sync:QuickShop"
                        )
                )

            val result = config.validateSyncDependencies()
            assertTrue(result.isLeft(), "Should return error when target not found")
            result.onLeft { error ->
                assertTrue(error is SyncDependencyError.TargetNotFound)
            }
        }

        @Test
        @DisplayName("Allows multi-level sync chains")
        fun testMultiLevelSyncIsAllowed() {
            // A <- sync:A の B <- sync:B の C。更新経路は多段でも追従を伝播できるため許可する
            val config =
                MpmConfig(
                    name = "test-project",
                    version = "1.0.0",
                    plugins =
                        mapOf(
                            "PluginA" to "1.0.0",
                            "PluginB" to "sync:PluginA",
                            "PluginC" to "sync:PluginB"
                        )
                )

            val result = config.validateSyncDependencies()
            assertTrue(result.isRight(), "Multi-level sync should pass validation")
        }

        @Test
        @DisplayName("Returns error when target is unmanaged")
        fun testTargetIsUnmanaged() {
            val config =
                MpmConfig(
                    name = "test-project",
                    version = "1.0.0",
                    plugins =
                        mapOf(
                            "PluginA" to "unmanaged",
                            "PluginB" to "sync:PluginA"
                        )
                )

            val result = config.validateSyncDependencies()
            assertTrue(result.isLeft(), "Should return error when target is unmanaged")
            result.onLeft { error ->
                assertTrue(error is SyncDependencyError.TargetIsUnmanaged)
            }
        }

        @Test
        @DisplayName("Returns error on circular sync")
        fun testCircularSyncIsRejected() {
            // 多段を許可しても循環は追従の起点が無いためエラーのままにする
            val config =
                MpmConfig(
                    name = "test-project",
                    version = "1.0.0",
                    plugins =
                        mapOf(
                            "PluginA" to "sync:PluginB",
                            "PluginB" to "sync:PluginA"
                        )
                )

            val result = config.validateSyncDependencies()
            assertTrue(result.isLeft(), "Circular sync should still be an error")
            result.onLeft { error ->
                assertTrue(error is SyncDependencyError.CircularDependency)
            }
        }

        @Test
        @DisplayName("Returns error on self-referencing sync")
        fun testSelfSyncIsRejected() {
            val config =
                MpmConfig(
                    name = "test-project",
                    version = "1.0.0",
                    plugins = mapOf("PluginA" to "sync:PluginA")
                )

            val result = config.validateSyncDependencies()
            assertTrue(result.isLeft(), "Self-referencing sync should still be an error")
            result.onLeft { error ->
                assertTrue(error is SyncDependencyError.CircularDependency)
            }
        }
    }

    @Nested
    @DisplayName("Circular dependency detection tests")
    inner class DetectCircularDependenciesTest {
        @Test
        @DisplayName("No circular dependencies returns null")
        fun testNoCircularDependencies() {
            val config =
                MpmConfig(
                    name = "test-project",
                    version = "1.0.0",
                    plugins =
                        mapOf(
                            "QuickShop" to "6.2.0",
                            "QuickShop-Addon" to "sync:QuickShop"
                        )
                )

            val result = config.detectCircularDependencies()
            assertNull(result, "Should return null when no circular dependencies")
        }

        @Test
        @DisplayName("Config without sync plugins returns null")
        fun testNoSyncPlugins() {
            val config =
                MpmConfig(
                    name = "test-project",
                    version = "1.0.0",
                    plugins =
                        mapOf(
                            "PluginA" to "1.0.0",
                            "PluginB" to "2.0.0"
                        )
                )

            val result = config.detectCircularDependencies()
            assertNull(result, "Should return null when no sync plugins")
        }
    }

    @Nested
    @DisplayName("Topological sort tests")
    inner class TopologicalSortPluginsTest {
        @Test
        @DisplayName("Sorts plugins with sync dependencies correctly")
        fun testTopologicalSort() {
            val config =
                MpmConfig(
                    name = "test-project",
                    version = "1.0.0",
                    plugins =
                        mapOf(
                            "QuickShop-Addon" to "sync:QuickShop",
                            "QuickShop" to "6.2.0",
                            "OtherPlugin" to "1.0.0"
                        )
                )

            val sorted = config.topologicalSortPlugins()

            // QuickShopはQuickShop-Addonより先に来る必要がある
            val quickShopIndex = sorted.indexOf("QuickShop")
            val addonIndex = sorted.indexOf("QuickShop-Addon")
            assertTrue(quickShopIndex < addonIndex, "QuickShop should come before QuickShop-Addon")
        }

        @Test
        @DisplayName("Orders multi-level sync as parent -> child -> grandchild")
        fun testMultiLevelTopologicalSort() {
            val config =
                MpmConfig(
                    name = "test-project",
                    version = "1.0.0",
                    plugins =
                        mapOf(
                            // 宣言順をわざと逆にしても、依存順に並ぶことを確認する
                            "C" to "sync:B",
                            "B" to "sync:A",
                            "A" to "latest"
                        )
                )

            val sorted = config.topologicalSortPlugins()

            assertTrue(sorted.indexOf("A") < sorted.indexOf("B"), "A should come before B")
            assertTrue(sorted.indexOf("B") < sorted.indexOf("C"), "B should come before C")
        }

        @Test
        @DisplayName("All plugins are included in sorted result")
        fun testAllPluginsIncluded() {
            val config =
                MpmConfig(
                    name = "test-project",
                    version = "1.0.0",
                    plugins =
                        mapOf(
                            "PluginA" to "1.0.0",
                            "PluginB" to "sync:PluginA",
                            "PluginC" to "2.0.0"
                        )
                )

            val sorted = config.topologicalSortPlugins()

            assertEquals(3, sorted.size, "All plugins should be included")
            assertTrue(sorted.contains("PluginA"))
            assertTrue(sorted.contains("PluginB"))
            assertTrue(sorted.contains("PluginC"))
        }
    }

    @Nested
    @DisplayName("Get sync dependencies tests")
    inner class GetSyncDependenciesTest {
        @Test
        @DisplayName("Returns sync dependencies correctly")
        fun testGetSyncDependencies() {
            val config =
                MpmConfig(
                    name = "test-project",
                    version = "1.0.0",
                    plugins =
                        mapOf(
                            "QuickShop" to "6.2.0",
                            "QuickShop-Addon" to "sync:QuickShop",
                            "OtherAddon" to "sync:QuickShop"
                        )
                )

            val syncDeps = config.getSyncDependencies()

            assertEquals(2, syncDeps.size)
            assertEquals("QuickShop", syncDeps["QuickShop-Addon"])
            assertEquals("QuickShop", syncDeps["OtherAddon"])
        }

        @Test
        @DisplayName("Returns empty map when no sync dependencies")
        fun testNoSyncDependencies() {
            val config =
                MpmConfig(
                    name = "test-project",
                    version = "1.0.0",
                    plugins =
                        mapOf(
                            "PluginA" to "1.0.0",
                            "PluginB" to "2.0.0"
                        )
                )

            val syncDeps = config.getSyncDependencies()

            assertTrue(syncDeps.isEmpty(), "Should return empty map")
        }
    }

    @Nested
    @DisplayName("Get plugins syncing to tests")
    inner class GetPluginsSyncingToTest {
        @Test
        @DisplayName("Returns plugins syncing to target")
        fun testGetPluginsSyncingTo() {
            val config =
                MpmConfig(
                    name = "test-project",
                    version = "1.0.0",
                    plugins =
                        mapOf(
                            "QuickShop" to "6.2.0",
                            "QuickShop-Addon1" to "sync:QuickShop",
                            "QuickShop-Addon2" to "sync:QuickShop",
                            "OtherPlugin" to "1.0.0"
                        )
                )

            val syncingPlugins = config.getPluginsSyncingTo("QuickShop")

            assertEquals(2, syncingPlugins.size)
            assertTrue(syncingPlugins.contains("QuickShop-Addon1"))
            assertTrue(syncingPlugins.contains("QuickShop-Addon2"))
        }

        @Test
        @DisplayName("Returns empty list when no plugins syncing")
        fun testNoPluginsSyncing() {
            val config =
                MpmConfig(
                    name = "test-project",
                    version = "1.0.0",
                    plugins =
                        mapOf(
                            "QuickShop" to "6.2.0",
                            "OtherPlugin" to "1.0.0"
                        )
                )

            val syncingPlugins = config.getPluginsSyncingTo("QuickShop")

            assertTrue(syncingPlugins.isEmpty(), "Should return empty list")
        }
    }

    @Nested
    @DisplayName("getSyncDescendants")
    inner class GetSyncDescendantsTest {
        @Test
        @DisplayName("Returns direct children")
        fun testDirectChildren() {
            val config =
                MpmConfig(
                    name = "test-project",
                    version = "1.0.0",
                    plugins =
                        mapOf(
                            "Parent" to "latest",
                            "Child" to "sync:Parent",
                            "Other" to "1.0.0"
                        )
                )

            assertEquals(listOf("Child"), config.getSyncDescendants("Parent"))
        }

        @Test
        @DisplayName("Propagates through multi-level sync in parent-first order")
        fun testMultiLevelSync() {
            val config =
                MpmConfig(
                    name = "test-project",
                    version = "1.0.0",
                    plugins =
                        mapOf(
                            "A" to "latest",
                            "C" to "sync:B",
                            "B" to "sync:A"
                        )
                )

            // 孫まで伝播し、親に近い順（B -> C）で列挙される
            assertEquals(listOf("B", "C"), config.getSyncDescendants("A"))
        }

        @Test
        @DisplayName("Does not loop on circular sync")
        fun testCircularSync() {
            val config =
                MpmConfig(
                    name = "test-project",
                    version = "1.0.0",
                    plugins =
                        mapOf(
                            "A" to "sync:B",
                            "B" to "sync:A"
                        )
                )

            // 起点自身は含まれず、無限ループにもならない
            assertEquals(listOf("A"), config.getSyncDescendants("B"))
        }
    }
}