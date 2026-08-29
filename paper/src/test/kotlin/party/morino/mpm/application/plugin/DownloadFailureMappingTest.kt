/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.shared.error.MpmError
import party.morino.mpm.infrastructure.downloader.PluginDownloadException

/**
 * ダウンロード失敗例外から型付きエラーへの変換（サービス境界での載せ替え）を検証するテスト
 */
@DisplayName("PluginDownloadException to MpmError mapping")
class DownloadFailureMappingTest {
    @Test
    @DisplayName("Rate limited and server side statuses become UpstreamUnavailable")
    fun temporaryStatusesBecomeUpstreamUnavailable() {
        val rateLimited = MpmError.DownloadError.HttpStatus("https://example.com/a.jar", 429)
        val serverError = MpmError.DownloadError.HttpStatus("https://example.com/a.jar", 503)

        assertTrue(
            PluginDownloadException(rateLimited).toMpmError("Sample") is
                MpmError.PluginError.UpstreamUnavailable,
            "429は上流の一時障害として扱うべき"
        )
        assertTrue(
            PluginDownloadException(serverError).toMpmError("Sample") is
                MpmError.PluginError.UpstreamUnavailable,
            "5xxは上流の一時障害として扱うべき"
        )
    }

    @Test
    @DisplayName("Other download errors are propagated as is")
    fun otherErrorsArePropagatedAsIs() {
        // クライアント起因ではない詳細（Content-Type不正など）は握り潰さずそのまま伝える
        val invalidContentType =
            MpmError.DownloadError.InvalidContentType("https://example.com/a.jar", "text/html")
        assertEquals(invalidContentType, PluginDownloadException(invalidContentType).toMpmError("Sample"))

        // 404は上流の一時障害ではないため、DownloadError.HttpStatusのまま返す
        val notFound = MpmError.DownloadError.HttpStatus("https://example.com/a.jar", 404)
        assertEquals(notFound, PluginDownloadException(notFound).toMpmError("Sample"))
    }
}