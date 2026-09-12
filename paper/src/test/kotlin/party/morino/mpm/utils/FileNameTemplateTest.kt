/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class FileNameTemplateTest {
    @Test
    @DisplayName("render expands both placeholders")
    fun testRenderExpandsPlaceholders() {
        val result =
            FileNameTemplate.render(
                "<pluginInfo.name>_<mpmInfo.version.current.normalized>.jar",
                "mpm",
                "0.0.26"
            )
        assertEquals("mpm_0.0.26.jar", result.getOrNull())
    }

    @Test
    @DisplayName("render falls back to the default template when null")
    fun testRenderDefaultTemplate() {
        assertEquals(
            "LuckPerms-5.4.97.jar",
            FileNameTemplate.render(null, "LuckPerms", "5.4.97").getOrNull()
        )
    }

    @Test
    @DisplayName("render leaves unknown placeholders untouched")
    fun testRenderUnknownPlaceholder() {
        assertEquals("<version>.jar", FileNameTemplate.render("<version>.jar", "Foo", "1.0.0").getOrNull())
    }

    @Test
    @DisplayName("render rejects a traversal template")
    fun testRenderRejectsTraversalTemplate() {
        // リポジトリ定義が `../` を含むテンプレートを持ち込んでも plugins/ の外へ出さない
        val result = FileNameTemplate.render("../../evil.jar", "Foo", "1.0.0")
        assertTrue(result.isLeft())
    }

    @Test
    @DisplayName("render rejects traversal injected through the plugin name")
    fun testRenderRejectsTraversalPluginName() {
        // 既定テンプレートでも、プラグイン名経由で区切り文字が混入しうる
        val result = FileNameTemplate.render(null, "../../evil", "1.0.0")
        assertTrue(result.isLeft())
    }
}