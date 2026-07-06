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
 * Hangarのバージョン一覧APIのレスポンス
 *
 * example: https://hangar.papermc.io/api/v1/projects/{slug}/versions
 * @param pagination ページネーション情報（全件数の把握に使用）
 * @param result バージョンのリスト（新しい順）
 */
@Serializable
data class HangarVersionsResponse(
    val pagination: HangarPagination,
    val result: List<HangarVersion>
)