/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class JarRetirementTest {
    @Test
    @DisplayName("isSameFile resolves relative and absolute paths to the same jar")
    fun testIsSameFile(
        @TempDir pluginsDir: File
    ) {
        val jar = File(pluginsDir, "mpm_0.0.26.jar").apply { writeText("new") }
        assertTrue(isSameFile(jar, File(pluginsDir, "./mpm_0.0.26.jar")))
        assertFalse(isSameFile(jar, File(pluginsDir, "mpm_0.0.25.jar")))
    }

    @Test
    @DisplayName("isLoadedJar detects a jar loaded by a running plugin")
    fun testIsLoadedJar(
        @TempDir pluginsDir: File
    ) {
        // 読み込み元のJARは、パスの書き方が違っても同じファイルとして判定する
        val loaded = File(pluginsDir, "MoripaUtils-0.1.6.jar").apply { writeText("old") }
        val placed = File(pluginsDir, "MoripaUtils-0.1.7.jar").apply { writeText("new") }
        assertTrue(isLoadedJar(File(pluginsDir, "./MoripaUtils-0.1.6.jar"), listOf(loaded)))
        assertFalse(isLoadedJar(placed, listOf(loaded)))
    }
}
