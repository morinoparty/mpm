/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.model.cache

import kotlinx.serialization.Serializable

/**
 * キャッシュに保存されている1エントリの情報
 *
 * @property key キャッシュキー（リクエストURLのSHA-256ハッシュ）
 * @property url キャッシュ元のリクエストURL。エントリが壊れている場合はファイル名を格納する
 * @property sizeBytes エントリファイルのサイズ（バイト）
 * @property fetchedAt 取得日時（ISO-8601形式）
 * @property expired TTLを超過しているかどうか
 */
@Serializable
data class CacheEntryInfo(
    val key: String,
    val url: String,
    val sizeBytes: Long,
    val fetchedAt: String,
    val expired: Boolean
)