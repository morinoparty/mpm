/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.downloader.hangar.data

import kotlinx.serialization.Serializable

/**
 * Hangar APIのページネーション情報
 * @param limit 1ページあたりの取得件数
 * @param offset 取得開始位置
 * @param count 全件数（全ページを巡回する終了判定に使用）
 */
@Serializable
data class HangarPagination(
    val limit: Long,
    val offset: Long,
    val count: Long
)