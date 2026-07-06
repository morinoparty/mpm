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
 * Hangarのプラットフォーム別ダウンロード情報
 *
 * Hangar上でホストされる場合は`downloadUrl`が、外部サイトでホストされる場合は`externalUrl`が設定される。
 * @param fileInfo ファイル情報（ファイル名やハッシュ）。外部ホストの場合はnull
 * @param externalUrl 外部ホストのダウンロードURL（Hangar外でホストされる場合）
 * @param downloadUrl Hangar上のダウンロードURL
 */
@Serializable
data class HangarPlatformDownload(
    val fileInfo: HangarFileInfo? = null,
    val externalUrl: String? = null,
    val downloadUrl: String? = null
)