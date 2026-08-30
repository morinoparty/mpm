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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * replaceJarAtomically（JAR差し替え）の主要な分岐を検証するテスト
 */
@DisplayName("replaceJarAtomically - staged jar replacement")
class JarReplacementTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    @DisplayName("Replaces the existing jar and removes both temp files")
    fun replacesExistingJarAndCleansUpTempFiles() {
        val target = File(tempDir, "Sample-1.0.0.jar").apply { writeText("old") }
        val downloaded = File(tempDir, "downloaded.tmp").apply { writeText("new") }

        val result = replaceJarAtomically(downloaded, target)

        assertTrue(result.isRight(), "置換に成功するはず: ${result.leftOrNull()}")
        assertEquals("new", target.readText())
        // ダウンロード済み一時ファイルとステージングファイルはどちらも残さない
        assertFalse(downloaded.exists(), "ダウンロード済み一時ファイルが残っている")
        assertFalse(File(tempDir, "Sample-1.0.0.jar.tmp").exists(), "ステージングファイルが残っている")
    }

    @Test
    @DisplayName("Keeps the existing jar intact when staging fails")
    fun keepsExistingJarWhenStagingFails() {
        val target = File(tempDir, "Sample-1.0.0.jar").apply { writeText("old") }
        // 存在しないファイルをダウンロード結果として渡し、ステージングを失敗させる
        val missing = File(tempDir, "missing.tmp")

        val result = replaceJarAtomically(missing, target)

        assertTrue(result.isLeft(), "ステージングに失敗した場合は Left を返すべき")
        // 既存JARが切り詰められず、そのまま残っていることが重要
        assertEquals("old", target.readText())
        assertFalse(File(tempDir, "Sample-1.0.0.jar.tmp").exists(), "ステージングファイルが残っている")
    }
}