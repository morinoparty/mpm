/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

import party.morino.mpm.api.shared.error.MpmError
import party.morino.mpm.infrastructure.downloader.PluginDownloadException

// レート制限を示すHTTPステータスコード（上流の一時障害として扱う）
private const val TOO_MANY_REQUESTS = 429

// サーバーエラーの下限（500以上は上流の一時障害として扱う）
private const val SERVER_ERROR_MIN = 500

/**
 * ダウンロード失敗例外が保持する型付きエラーを、サービス境界で使う [MpmError] へ変換する
 *
 * リトライを尽くしても 429 / 5xx だった場合は上流リポジトリの一時障害とみなし、
 * [MpmError.PluginError.UpstreamUnavailable] へ載せ替える（HTTPでは503となり、
 * クライアントに再試行の余地が伝わる）。
 * それ以外は保持している [MpmError.DownloadError] をそのまま伝播させ、
 * 原因（Content-Type不正・サイズ不一致など）を握り潰さない。
 *
 * @param pluginName 対象プラグイン名
 * @return HTTPステータスへのマッピングまで考慮した型付きエラー
 */
internal fun PluginDownloadException.toMpmError(pluginName: String): MpmError {
    val downloadError = this.error as? MpmError.DownloadError.HttpStatus ?: return this.error
    val isTemporary =
        downloadError.statusCode == TOO_MANY_REQUESTS || downloadError.statusCode >= SERVER_ERROR_MIN
    return if (isTemporary) {
        MpmError.PluginError.UpstreamUnavailable(pluginName, downloadError.message)
    } else {
        downloadError
    }
}