/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * AtomicFileWriterのテスト
 *
 * 「原子的に置き換えられない場合、元ファイルを壊さない」という要求を検証する
 */
class AtomicFileWriterTest {
    @TempDir
    lateinit var rootDir: File

    @Test
    @DisplayName("replaces an existing file and leaves no temp file")
    fun replacesExistingFile() {
        val target = File(rootDir, "mpm.json").apply { writeText("old") }

        val result = AtomicFileWriter.write(target, "new")

        assertTrue(result.isRight())
        assertEquals("new", target.readText())
        // 一時ファイルが残っていない（名前は毎回一意なので拡張子で走査する）
        assertFalse(hasLeftoverTempFile())
    }

    @Test
    @DisplayName("keeps the target intact when the atomic move fails")
    fun keepsTargetIntactWhenMoveFails() {
        // 中身のあるディレクトリを書き込み先に見立てると move が必ず失敗するため、
        // 「置き換えられなかったとき元を壊さないか」を確実に検証できる
        val target = File(rootDir, "metadata").apply { mkdirs() }
        val survivor = File(target, "CarbonChat.yaml").apply { writeText("keep-me") }

        val result = AtomicFileWriter.write(target, "破壊的な書き込み")

        // フォールバックせずに失敗として返る
        assertTrue(result.isLeft())
        // 元ファイルは一切触られていない
        assertTrue(target.isDirectory)
        assertEquals("keep-me", survivor.readText())
        // 一時ファイルは後始末されている
        assertFalse(hasLeftoverTempFile())
    }

    @Test
    @DisplayName("does not reuse a fixed temp file name")
    fun doesNotReuseFixedTempFileName() {
        val target = File(rootDir, "mpm.json").apply { writeText("old") }
        // 固定名の一時ファイルを別の書き込みが握っている状況を再現する。
        // 一時ファイル名が固定だと、この内容と混線したり後始末で消したりしてしまう。
        val squatter = File(rootDir, "mpm.json.tmp").apply { writeText("別スレッドの書きかけ") }

        val result = AtomicFileWriter.write(target, "new")

        assertTrue(result.isRight())
        assertEquals("new", target.readText())
        // 他の書き込みが握っている一時ファイルには一切触れていない
        assertEquals("別スレッドの書きかけ", squatter.readText())
    }

    /**
     * 書き込み先ディレクトリに一時ファイルが残っていないかを調べる
     *
     * 一時ファイル名は同時書き込みで混線しないよう毎回一意にしているため、
     * 固定名ではなく拡張子で走査する。
     */
    private fun hasLeftoverTempFile(): Boolean = rootDir.listFiles().orEmpty().any { it.name.endsWith(".tmp") }
}