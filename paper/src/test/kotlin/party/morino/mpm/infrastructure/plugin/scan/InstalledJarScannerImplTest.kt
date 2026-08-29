/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
 * and related and neighboring rights to this software to the public domain worldwide.
 * This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.plugin.scan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/**
 * InstalledJarScannerImplのテスト
 *
 * DIを介さずに純粋な走査ロジック（scanDirectory）を検証する
 */
@DisplayName("InstalledJarScannerImpl tests")
class InstalledJarScannerImplTest {
    @TempDir
    lateinit var pluginsDir: File

    /**
     * 指定したYAMLエントリを持つJARファイルをテンポラリディレクトリに作成する
     *
     * @param fileName 作成するJARのファイル名
     * @param entryName JAR内のエントリ名（plugin.yml / paper-plugin.yml）
     * @param yaml エントリの中身。nullの場合はプラグイン情報を持たないJARを作る
     */
    private fun createJar(
        fileName: String,
        entryName: String? = null,
        yaml: String? = null
    ): File {
        val jarFile = File(pluginsDir, fileName)
        JarOutputStream(jarFile.outputStream()).use { out ->
            // エントリが1件もないとZIPとして不正になるため、ダミーエントリを入れる
            out.putNextEntry(JarEntry(entryName ?: "dummy.txt"))
            out.write((yaml ?: "dummy").toByteArray())
            out.closeEntry()
        }
        return jarFile
    }

    @Test
    @DisplayName("scanDirectory should read names from plugin.yml and paper-plugin.yml")
    fun scanReadsBothFormats() {
        createJar("bukkit.jar", "plugin.yml", "name: Alpha\nversion: 1.0.0\n")
        createJar("paper.jar", "paper-plugin.yml", "name: Beta\nversion: 2.0.0\n")

        val result = InstalledJarScannerImpl.scanDirectory(pluginsDir, "mpm")

        assertEquals(setOf("Alpha", "Beta"), result.map { it.name }.toSet())
        assertEquals("1.0.0", result.first { it.name == "Alpha" }.version)
        assertEquals("2.0.0", result.first { it.name == "Beta" }.version)
    }

    @Test
    @DisplayName("scanDirectory should skip self, blank names and non-plugin files")
    fun scanSkipsInvalidEntries() {
        createJar("valid.jar", "plugin.yml", "name: Alpha\nversion: 1.0.0\n")
        // MPM自身は除外される
        createJar("mpm.jar", "plugin.yml", "name: mpm\nversion: 1.0.0\n")
        // 名前が空のプラグインは除外される（PluginNameを構築できないため）
        createJar("blank.jar", "plugin.yml", "version: 1.0.0\n")
        // plugin.ymlを持たないJARは除外される
        createJar("nometa.jar")
        // JAR以外のファイルは走査対象外
        File(pluginsDir, "readme.txt").writeText("not a jar")

        val result = InstalledJarScannerImpl.scanDirectory(pluginsDir, "mpm")

        assertEquals(listOf("Alpha"), result.map { it.name })
    }

    @Test
    @DisplayName("scanDirectory should return empty list for missing directory")
    fun scanReturnsEmptyForMissingDirectory() {
        val missing = File(pluginsDir, "not-exists")

        assertEquals(emptyList<String>(), InstalledJarScannerImpl.scanDirectory(missing, "mpm").map { it.name })
    }
}