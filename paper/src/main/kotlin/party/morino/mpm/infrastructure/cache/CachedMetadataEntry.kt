/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.cache

import kotlinx.serialization.Serializable

/**
 * キャッシュファイルに保存されるメタデータエントリ
 *
 * TTLは保存せず、読み出し時に現在の設定値と比較して有効期限を判定する。
 * これにより設定変更が既存エントリへ即座に反映される。
 *
 * @property url キャッシュ元のリクエストURL（ハッシュ衝突検知とlist表示に使用）
 * @property fetchedAtEpochSeconds 取得時刻（エポック秒）
 * @property body レスポンス本文
 */
@Serializable
data class CachedMetadataEntry(
    val url: String,
    val fetchedAtEpochSeconds: Long,
    val body: String
)