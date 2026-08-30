/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.downloader

import party.morino.mpm.api.shared.error.MpmError

/**
 * ダウンロード失敗の原因を型付きで運ぶ例外
 *
 * [party.morino.mpm.api.domain.downloader.PluginDownloader] のダウンロードAPIは
 * 戻り値が `File?` のため、失敗理由をそのまま返せない。
 * nullを返して原因を握りつぶす代わりにこの例外を投げることで、
 * 呼び出し側（PluginLifecycleServiceImpl等）が `e.message` を通じて
 * 具体的な失敗理由（HTTPステータス・Content-Type不正・サイズ不一致）を利用者へ提示できる。
 *
 * @property error 失敗の内容を表す型付きエラー
 */
class PluginDownloadException(
    val error: MpmError
) : Exception(error.message)