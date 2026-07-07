/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.MpmTest
import party.morino.mpm.api.application.plugin.IntegrityVerifier
import party.morino.mpm.api.application.plugin.model.integrity.IntegrityResult
import party.morino.mpm.api.application.plugin.model.integrity.IntegritySource
import party.morino.mpm.api.domain.downloader.model.UrlData
import java.io.File

/**
 * IntegrityVerifierImpl のハッシュ計算と検証分岐のテスト
 *
 * リモートハッシュを提供しないGitHub URLを用いることで、ネットワークアクセスなしに
 * 「保存済みsha256（trust-on-first-use）」と「照合先なし」の分岐を検証する。
 */
@ExtendWith(MpmTest::class)
@DisplayName("IntegrityVerifier - hash computation and verification")
class IntegrityVerifierImplTest : KoinComponent {
    // テスト対象（実装クラスをKoinで注入）
    private val integrityVerifier: IntegrityVerifier by inject()

    // ハッシュ取得非対応のリポジトリ（GitHubはハッシュを提供しないためnullが返る）
    private val githubUrl = UrlData.GithubUrlData(owner = "owner", repository = "repo")

    // "hello" の既知のSHA-256（小文字16進）
    private val helloSha256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"

    private fun writeFile(
        dir: File,
        content: String
    ): File {
        val file = File(dir, "plugin.jar")
        file.writeText(content)
        return file
    }

    @Test
    @DisplayName("computeSha256 returns the known SHA-256 of the content")
    fun computeSha256ReturnsKnownHash(
        @TempDir tempDir: File
    ) {
        val file = writeFile(tempDir, "hello")
        assertEquals(helloSha256, integrityVerifier.computeSha256(file))
    }

    @Test
    @DisplayName("verify returns NoReference when no hash is available")
    fun verifyReturnsNoReferenceWithoutHash(
        @TempDir tempDir: File
    ) = runBlocking {
        val file = writeFile(tempDir, "hello")
        // ストア済みハッシュなし・リモートハッシュなし → 照合不能
        val result = integrityVerifier.verify(file, githubUrl, "1.0.0", storedSha256 = null)
        val noRef = assertInstanceOf(IntegrityResult.NoReference::class.java, result)
        assertEquals(helloSha256, noRef.sha256)
    }

    @Test
    @DisplayName("verify returns Verified(STORED) when the stored sha256 matches")
    fun verifyReturnsVerifiedWhenStoredMatches(
        @TempDir tempDir: File
    ) = runBlocking {
        val file = writeFile(tempDir, "hello")
        // 保存済みsha256と一致（trust-on-first-use）
        val result = integrityVerifier.verify(file, githubUrl, "1.0.0", storedSha256 = helloSha256)
        val verified = assertInstanceOf(IntegrityResult.Verified::class.java, result)
        assertEquals(IntegritySource.STORED, verified.source)
    }

    @Test
    @DisplayName("verify returns Mismatch when the stored sha256 differs")
    fun verifyReturnsMismatchWhenStoredDiffers(
        @TempDir tempDir: File
    ) = runBlocking {
        val file = writeFile(tempDir, "hello")
        // 保存済みsha256と不一致（破損・改竄を検出）
        val result = integrityVerifier.verify(file, githubUrl, "1.0.0", storedSha256 = "deadbeef")
        val mismatch = assertInstanceOf(IntegrityResult.Mismatch::class.java, result)
        assertEquals("SHA-256", mismatch.algorithm)
        assertEquals(helloSha256, mismatch.actual)
    }
}