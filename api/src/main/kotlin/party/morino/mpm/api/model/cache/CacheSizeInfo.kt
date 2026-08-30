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
 * キャッシュディレクトリ全体のサイズ情報
 *
 * @property totalSizeBytes キャッシュディレクトリ配下の合計サイズ（バイト）
 * @property entryCount キャッシュエントリ（ファイル）の総数
 * @property expiredEntryCount TTLを超過しているメタデータエントリの数
 */
@Serializable
data class CacheSizeInfo(
    val totalSizeBytes: Long,
    val entryCount: Int,
    val expiredEntryCount: Int
)