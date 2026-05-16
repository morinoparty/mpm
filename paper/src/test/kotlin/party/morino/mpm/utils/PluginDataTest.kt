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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
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

    @Test
    @DisplayName("SnakeYAML cannot distinguish unquoted 1.2 from 1.20")
    fun testSnakeYamlCannotDistinguish() {
        val yaml = Yaml(SafeConstructor(LoaderOptions()))

        // SnakeYAML parses both as the same Double
        val parsed120 = yaml.load<Map<String, Any>>("api-version: 1.20")
        val parsed12 = yaml.load<Map<String, Any>>("api-version: 1.2")
        assertEquals(
            parsed120["api-version"],
            parsed12["api-version"],
            "SnakeYAML treats unquoted 1.2 and 1.20 as the same Double"
        )

        // parseApiVersion restores trailing zero for both
        assertEquals("1.20", PluginDataUtils.parseApiVersion(parsed120["api-version"]))
        assertEquals("1.20", PluginDataUtils.parseApiVersion(parsed12["api-version"]))
    }

    @Test
    @DisplayName("Quoted strings are preserved correctly by SnakeYAML")
    fun testSnakeYamlQuotedStringsPreserved() {
        val yaml = Yaml(SafeConstructor(LoaderOptions()))

        // Quoted values are preserved as strings
        val quoted120 = yaml.load<Map<String, Any>>("api-version: '1.20'")
        val quoted12 = yaml.load<Map<String, Any>>("api-version: '1.2'")

        assertEquals("1.20", PluginDataUtils.parseApiVersion(quoted120["api-version"]))
        assertEquals("1.2", PluginDataUtils.parseApiVersion(quoted12["api-version"]))
        assertNotEquals(
            PluginDataUtils.parseApiVersion(quoted120["api-version"]),
            PluginDataUtils.parseApiVersion(quoted12["api-version"]),
            "Quoted strings should be distinguishable"
        )
    }
}