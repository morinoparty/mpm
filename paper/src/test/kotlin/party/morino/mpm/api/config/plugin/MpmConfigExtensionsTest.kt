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

package party.morino.mpm.api.config.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
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
}
