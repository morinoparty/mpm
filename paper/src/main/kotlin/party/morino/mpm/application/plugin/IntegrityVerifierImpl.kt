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

package party.morino.mpm.application.plugin

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.application.plugin.IntegrityVerifier
import party.morino.mpm.api.application.plugin.model.integrity.IntegrityResult
import party.morino.mpm.api.application.plugin.model.integrity.IntegritySource
import party.morino.mpm.api.domain.downloader.DownloaderRepository
import party.morino.mpm.api.domain.downloader.model.UrlData
import java.io.File
import java.security.MessageDigest

/**
 * [IntegrityVerifier] の実装
 *
 * リポジトリ提供ハッシュ（Modrinth: sha1/sha512、Hangar: sha256）や
 * メタデータに保存済みのsha256と照合して、ダウンロードしたJARの整合性を検証する。
 */
class IntegrityVerifierImpl :
    IntegrityVerifier,
    KoinComponent {
    // Koinによる依存性注入
    private val downloaderRepository: DownloaderRepository by inject()

    override suspend fun verify(
        file: File,
        urlData: UrlData,
        versionName: String,
        storedSha256: String?,
        fileNamePattern: String?
    ): IntegrityResult {
        // どの分岐でも保存できるよう、まずsha256を計算しておく（trust-on-first-use用）
        val actualSha256 = computeDigest(file, "SHA-256")

        // 1. リポジトリが提供するハッシュを優先的に照合する
        //    ハッシュ取得の失敗（通信エラー等）は検証不能として握りつぶし、保存済みハッシュへフォールバックする
        val repoHashes =
            try {
                downloaderRepository.getVersionHashesByName(urlData, versionName, fileNamePattern)
            } catch (_: Exception) {
                null
            }

        if (repoHashes != null) {
            // 強度の高いアルゴリズムから順に照合する（sha256 → sha512 → sha1）
            repoHashes["sha256"]?.let { reference ->
                return compareOrMismatch("SHA-256", reference, actualSha256, actualSha256, IntegritySource.REMOTE)
            }
            repoHashes["sha512"]?.let { reference ->
                val actual = computeDigest(file, "SHA-512")
                return compareOrMismatch("SHA-512", reference, actual, actualSha256, IntegritySource.REMOTE)
            }
            repoHashes["sha1"]?.let { reference ->
                val actual = computeDigest(file, "SHA-1")
                return compareOrMismatch("SHA-1", reference, actual, actualSha256, IntegritySource.REMOTE)
            }
        }

        // 2. リモートハッシュがなければ、メタデータに保存済みのsha256と照合する（trust-on-first-use）
        if (!storedSha256.isNullOrBlank()) {
            return if (storedSha256.equals(actualSha256, ignoreCase = true)) {
                IntegrityResult.Verified(actualSha256, IntegritySource.STORED)
            } else {
                IntegrityResult.Mismatch(actualSha256, "SHA-256", storedSha256, actualSha256)
            }
        }

        // 3. 照合先が存在しない場合は検証不能（sha256は保存して次回以降に備える）
        return IntegrityResult.NoReference(actualSha256)
    }

    override fun computeSha256(file: File): String = computeDigest(file, "SHA-256")

    /**
     * 期待ハッシュと実ハッシュを照合し、結果を返す
     *
     * 期待ハッシュはカンマ区切りの複数値（複数artifact）を許容し、
     * いずれか1つでも一致すれば検証成功とする（既存のpin検証と同じ挙動）。
     *
     * @param algorithm 照合に使用したアルゴリズム名
     * @param referenceCsv 期待ハッシュ（カンマ区切り可）
     * @param actual 実際に計算したハッシュ（[algorithm] に対応）
     * @param sha256 保存用に計算したsha256
     * @param source ハッシュの出所
     */
    private fun compareOrMismatch(
        algorithm: String,
        referenceCsv: String,
        actual: String,
        sha256: String,
        source: IntegritySource
    ): IntegrityResult {
        val references = referenceCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        // 照合対象がなければ検証不能扱いとする
        if (references.isEmpty()) {
            return IntegrityResult.NoReference(sha256)
        }
        val matched = references.any { it.equals(actual, ignoreCase = true) }
        return if (matched) {
            IntegrityResult.Verified(sha256, source)
        } else {
            IntegrityResult.Mismatch(sha256, algorithm, references.joinToString(","), actual)
        }
    }

    /**
     * 指定アルゴリズムでファイルのハッシュを計算する
     *
     * @param file ハッシュを計算するファイル
     * @param algorithm JCAアルゴリズム名（"SHA-256" / "SHA-512" / "SHA-1"）
     * @return ハッシュの16進数小文字文字列
     */
    private fun computeDigest(
        file: File,
        algorithm: String
    ): String {
        val digest = MessageDigest.getInstance(algorithm)
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}