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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import party.morino.mpm.api.domain.downloader.model.TargetPlatform
import party.morino.mpm.api.model.plugin.PluginData
import party.morino.mpm.api.model.plugin.VelocityDependency
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class PluginDataTest {
    @Test
    fun testLoadBukkitPluginData() {
        val bukkitPluginFile = File("src/test/resources/BukkitPlugin.jar")
        val bukkitPluginData =
            PluginDataUtils.getPluginData(
                bukkitPluginFile,
                TargetPlatform.PAPER
            ) as PluginData.BukkitPluginData
        assertNotNull("dev.nikomaru.minecraftpluginmanager.MinecraftPluginManager", bukkitPluginData.main)
    }

    @Test
    fun testLoadPaperPluginData() {
        val paperPluginFile = File("src/test/resources/PaperPlugin.jar")
        val paperPluginData =
            PluginDataUtils.getPluginData(
                paperPluginFile,
                TargetPlatform.PAPER
            ) as PluginData.PaperPluginData
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

    @Test
    @DisplayName("parseVelocityPluginJson maps id, version and dependencies")
    fun testParseVelocityPluginJson() {
        // Velocity の注釈処理が生成する形式（未知のキーは読み飛ばす）
        val json =
            """
            {"id":"example","name":"Example","version":"1.2.3","main":"a.b.Example",
             "authors":["nikomaru"],"dependencies":[{"id":"luckperms","optional":false},{"id":"miniplaceholders","optional":true}],
             "unknown":"ignored"}
            """.trimIndent()
        val data = PluginDataUtils.parseVelocityPluginJson(json)

        // name にはプラグインIDが入り、表示名は displayName に分かれる
        assertEquals("example", data.name)
        assertEquals("Example", data.displayName)
        assertEquals("1.2.3", data.version)
        assertEquals(
            listOf(VelocityDependency("luckperms", false), VelocityDependency("miniplaceholders", true)),
            data.dependencies
        )
        assertEquals(listOf("luckperms"), data.depend)
        assertEquals(listOf("miniplaceholders"), data.softDepend)
    }

    @Test
    @DisplayName("getPluginData prefers the descriptor of the running platform")
    fun testDescriptorPriorityByPlatform(
        @TempDir dir: File
    ) {
        // plugin.yml と velocity-plugin.json の両方を持つ、複数プラットフォーム向けのJAR
        val universal = File(dir, "universal.jar")
        writeJar(
            universal,
            "plugin.yml" to "name: Universal\nversion: 1.0.0\nmain: a.b.Bukkit\n",
            "velocity-plugin.json" to """{"id":"universal","version":"1.0.0","main":"a.b.Velocity"}"""
        )

        // Paper では plugin.yml、Velocity では velocity-plugin.json を読む
        assertTrue(PluginDataUtils.getPluginData(universal, TargetPlatform.PAPER) is PluginData.BukkitPluginData)
        assertEquals("universal", PluginDataUtils.getPluginData(universal, TargetPlatform.VELOCITY)?.name)
    }

    @Test
    @DisplayName("Velocity-only jar is read on Paper but not supported")
    fun testVelocityOnlyJarIsNotSupportedOnPaper(
        @TempDir dir: File
    ) {
        val velocityOnly = File(dir, "velocity-only.jar")
        writeJar(velocityOnly, "velocity-plugin.json" to """{"id":"proxyonly","version":"2.0.0","main":"a.b.C"}""")

        // 他のプラットフォーム向けでも記述子は読み取れる（インストール前の検証で拒否するため）
        val data = PluginDataUtils.getPluginData(velocityOnly, TargetPlatform.PAPER)
        assertTrue(data is PluginData.VelocityPluginData)
        assertFalse(TargetPlatform.PAPER.supports(data!!))
        assertTrue(TargetPlatform.VELOCITY.supports(data))
    }

    /**
     * 指定したエントリだけを含むJARを書き出す
     */
    private fun writeJar(
        file: File,
        vararg entries: Pair<String, String>
    ) {
        JarOutputStream(file.outputStream()).use { jar ->
            entries.forEach { (name, content) ->
                jar.putNextEntry(JarEntry(name))
                jar.write(content.toByteArray())
                jar.closeEntry()
            }
        }
    }
}