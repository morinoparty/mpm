/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.downloader.spigot.data

import kotlinx.serialization.Serializable

/**
 * SpigotMCバージョン情報
 * id/nameが欠落したレスポンスでも全体のデコードが失敗しないよう、
 * どちらもnull許容+デフォルト値としている（欠落時は呼び出し側で"unknown"にフォールバックする）
 * @param id ダウンロードID ex) 55234
 * @param name バージョン名 ex) 1.0.0
 */
@Serializable
data class SpigotVersionInfo(
    val id: Int? = null,
    val name: String? = null
)