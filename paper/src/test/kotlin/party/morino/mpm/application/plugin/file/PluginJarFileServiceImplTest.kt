/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin.file

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import party.morino.mpm.api.shared.error.MpmError
import java.io.File
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

@DisplayName("PluginJarFileServiceImpl pure helpers")
class PluginJarFileServiceImplTest {
    @Test
    @DisplayName("resolves an existing jar directly under plugins/")
    fun resolvesExistingJar(
        @TempDir pluginsDir: File
    ) {
        val jar = File(pluginsDir, "mpm_0.0.25.jar").apply { writeText("old") }

        val result = PluginJarFileServiceImpl.resolveJarFile(pluginsDir, "mpm_0.0.25.jar")

        assertEquals(jar.canonicalFile, result.getOrNull()?.canonicalFile)
    }

    @Test
    @DisplayName("rejects path traversal and nested paths as invalid file names")
    fun rejectsPathTraversal(
        @TempDir pluginsDir: File
    ) {
        // plugins/ の外や、サブディレクトリ配下のファイルは指定できない
        listOf("../server.jar", "sub/plugin.jar", "..", "C:evil.jar").forEach { name ->
            val error = PluginJarFileServiceImpl.resolveJarFile(pluginsDir, name).leftOrNull()
            assertTrue(error is MpmError.FileError.InvalidFileName, "$name should be rejected, got $error")
        }
    }

    @Test
    @DisplayName("rejects files that are not jars even if they exist")
    fun rejectsNonJarFiles(
        @TempDir pluginsDir: File
    ) {
        File(pluginsDir, "config.yml").writeText("x")

        val error = PluginJarFileServiceImpl.resolveJarFile(pluginsDir, "config.yml").leftOrNull()

        assertTrue(error is MpmError.FileError.InvalidFileName, "got $error")
    }

    @Test
    @DisplayName("rejects a directory named like a jar")
    fun rejectsDirectory(
        @TempDir pluginsDir: File
    ) {
        File(pluginsDir, "dir.jar").mkdir()

        val error = PluginJarFileServiceImpl.resolveJarFile(pluginsDir, "dir.jar").leftOrNull()

        assertTrue(error is MpmError.FileError.InvalidFileName, "got $error")
    }

    @Test
    @DisplayName("scanJarFiles lists only jars directly under plugins/ in name order")
    fun scanJarFilesListsTopLevelJars(
        @TempDir pluginsDir: File
    ) {
        File(pluginsDir, "b.jar").writeText("b")
        File(pluginsDir, "a.jar").writeText("a")
        File(pluginsDir, "config.yml").writeText("x")
        // サブディレクトリ配下は対象外
        File(pluginsDir, "sub").mkdir()
        File(pluginsDir, "sub/nested.jar").writeText("n")

        val names = PluginJarFileServiceImpl.scanJarFiles(pluginsDir).map { it.name }

        assertEquals(listOf("a.jar", "b.jar"), names)
    }

    @Test
    @DisplayName("describeJar reads plugin.yml and tolerates broken jars")
    fun describeJarReadsPluginYml(
        @TempDir pluginsDir: File
    ) {
        // plugin.yml を持つ最小のJAR
        val valid = File(pluginsDir, "Example.jar")
        JarOutputStream(valid.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("plugin.yml"))
            out.write("name: Example\nversion: 1.2.3\nmain: a.b.C\n".toByteArray())
            out.closeEntry()
        }
        // ZIPとして壊れているJAR
        val broken = File(pluginsDir, "broken.jar").apply { writeText("not a zip") }

        val validInfo = PluginJarFileServiceImpl.describeJar(valid, running = false, pendingDeletion = true)
        val brokenInfo = PluginJarFileServiceImpl.describeJar(broken, running = true, pendingDeletion = false)

        assertEquals("Example", validInfo.pluginName)
        assertEquals("1.2.3", validInfo.pluginVersion)
        assertTrue(validInfo.pendingDeletion)
        // 壊れたJARでも一覧から落とさず、プラグイン情報だけ null にする
        assertEquals("broken.jar", brokenInfo.fileName)
        assertNull(brokenInfo.pluginName)
        assertTrue(brokenInfo.running)
    }

    @Test
    @DisplayName("returns NotFound for a missing jar")
    fun returnsNotFoundForMissingJar(
        @TempDir pluginsDir: File
    ) {
        val error = PluginJarFileServiceImpl.resolveJarFile(pluginsDir, "missing.jar").leftOrNull()

        assertEquals(MpmError.FileError.NotFound("missing.jar"), error)
    }
}