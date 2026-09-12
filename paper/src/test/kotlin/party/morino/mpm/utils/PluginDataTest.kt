/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.model.plugin.PluginData
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
    @DisplayName("extractRawApiVersion keeps unquoted 1.9 as written")
    fun testExtractRawApiVersionSingleDigitMinor() {
        // 以前は 1.9 が 1.90 に化けて 1.21 サーバーで非互換扱いになっていた
        assertEquals("1.9", PluginDataUtils.extractRawApiVersion("name: Foo\napi-version: 1.9\nmain: a.b.C\n"))
    }

    @Test
    @DisplayName("extractRawApiVersion distinguishes unquoted 1.20 from 1.2")
    fun testExtractRawApiVersionTrailingZero() {
        assertEquals("1.20", PluginDataUtils.extractRawApiVersion("api-version: 1.20"))
        assertEquals("1.2", PluginDataUtils.extractRawApiVersion("api-version: 1.2"))
    }

    @Test
    @DisplayName("extractRawApiVersion strips quotes, comments and CRLF")
    fun testExtractRawApiVersionQuotesCommentsCrlf() {
        assertEquals("1.20", PluginDataUtils.extractRawApiVersion("api-version: '1.20'"))
        assertEquals("1.21", PluginDataUtils.extractRawApiVersion("api-version: \"1.21\""))
        assertEquals("1.13", PluginDataUtils.extractRawApiVersion("api-version: 1.13 # oldest supported"))
        assertEquals("1.21", PluginDataUtils.extractRawApiVersion("name: Foo\r\napi-version:\t1.21\r\nmain: a\r\n"))
        assertEquals("1.20", PluginDataUtils.extractRawApiVersion("\uFEFFapi-version: 1.20\nname: Foo\n"))
    }

    @Test
    @DisplayName("extractRawApiVersion ignores nested keys and returns null when absent")
    fun testExtractRawApiVersionNestedAndMissing() {
        // ネストした api-version はトップレベルのキーではないので拾わない
        assertNull(PluginDataUtils.extractRawApiVersion("dependencies:\n  api-version: 1.20\n"))
        assertNull(PluginDataUtils.extractRawApiVersion("name: Foo\n"))
        assertNull(PluginDataUtils.extractRawApiVersion("api-version:\n"))
    }

    @Test
    @DisplayName("parseApiVersion fallback stringifies without guessing trailing zero")
    fun testParseApiVersionFallback() {
        assertEquals("1.21", PluginDataUtils.parseApiVersion("1.21"))
        assertEquals("1.9", PluginDataUtils.parseApiVersion(1.9))
        assertEquals("", PluginDataUtils.parseApiVersion(null))
    }
}