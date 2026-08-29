/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.cache

import arrow.core.Option

/**
 * HTTPメタデータ（バージョン一覧・プロジェクト情報など）のキャッシュを扱うインターフェース
 *
 * リクエストURLをキーとし、TTLを超過していないレスポンス本文を返す。
 * ダウンローダーの取得処理を短絡させ、ネットワーク往復とレート制限の消費を抑える目的で使用する。
 */
interface HttpMetadataCache {
    /**
     * キャッシュされたレスポンス本文を取得する
     *
     * キャッシュが無効・未ヒット・TTL超過・エントリ破損のいずれの場合もNoneを返す。
     *
     * @param url リクエストURL（キャッシュキー）
     * @return 有効なキャッシュ本文、存在しない場合はNone
     */
    fun get(url: String): Option<String>

    /**
     * レスポンス本文をキャッシュへ保存する
     *
     * 保存はベストエフォートであり、失敗しても呼び出し側の処理は継続する。
     *
     * @param url リクエストURL（キャッシュキー）
     * @param body 保存するレスポンス本文
     */
    fun put(
        url: String,
        body: String
    )

    /**
     * 指定URLのキャッシュエントリを破棄する
     *
     * @param url リクエストURL（キャッシュキー）
     */
    fun invalidate(url: String)
}