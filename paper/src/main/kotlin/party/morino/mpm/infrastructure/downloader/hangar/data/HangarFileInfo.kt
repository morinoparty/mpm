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
 * Hangarのファイル情報
 * @param name ファイル名（ダウンロード後のファイル名に使用）
 * @param sizeBytes ファイルサイズ（バイト）
 * @param sha256Hash SHA-256ハッシュ（Hangarが提供する唯一のハッシュ。インストール時の整合性検証に使用）
 */
@Serializable
data class HangarFileInfo(
    val name: String,
    val sizeBytes: Long = 0,
    val sha256Hash: String? = null
)