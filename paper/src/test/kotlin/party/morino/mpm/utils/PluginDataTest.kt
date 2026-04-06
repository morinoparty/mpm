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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.model.plugin.PluginData
import party.morino.mpm.utils.PluginDataUtils
import java.io.File

class PluginDataTest {
    @Test
    fun testLoadBukkitPluginData() {
        val bukkitPluginFile = File("src/test/resources/BukkitPlugin.jar")
        val bukkitPluginData = PluginDataUtils.getPluginData(bukkitPluginFile) as PluginData.BukkitPluginData
        assertNotNull("dev.nikomaru.minecraftpluginmanager.MinecraftPluginManager", bukkitPluginData.main)
    }

    @Test
    fun testLoadPaperPluginData() {
        val paperPluginFile = File("src/test/resources/PaperPlugin.jar")
        val paperPluginData = PluginDataUtils.getPluginData(paperPluginFile) as PluginData.PaperPluginData
        println(paperPluginData)
        assertEquals("1.20", paperPluginData.apiVersion)
    }

    @Test
    @DisplayName("parseApiVersion preserves trailing zero from SnakeYAML float")
    fun testParseApiVersionTrailingZero() {
        // SnakeYAML parses "api-version: 1.20" as Double 1.2
        assertEquals("1.20", PluginDataUtils.parseApiVersion(1.2))
    }

    @Test
    @DisplayName("parseApiVersion keeps normal version string as-is")
    fun testParseApiVersionString() {
        assertEquals("1.21", PluginDataUtils.parseApiVersion("1.21"))
        assertEquals("1.20", PluginDataUtils.parseApiVersion("1.20"))
    }

    @Test
    @DisplayName("parseApiVersion handles multi-digit minor version")
    fun testParseApiVersionMultiDigit() {
        // SnakeYAML parses "api-version: 1.21" as Double 1.21
        assertEquals("1.21", PluginDataUtils.parseApiVersion(1.21))
        assertEquals("1.13", PluginDataUtils.parseApiVersion(1.13))
    }

    @Test
    @DisplayName("parseApiVersion handles null and empty")
    fun testParseApiVersionNullEmpty() {
        assertEquals("", PluginDataUtils.parseApiVersion(null))
        assertEquals("", PluginDataUtils.parseApiVersion(""))
    }
}