/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth.model.plugin

import kotlinx.serialization.Serializable
import party.morino.mpm.api.domain.plugin.dto.MetadataDownloadInfoDto

/**
 * メタデータに記録されたダウンロード情報のレスポンス
 *
 * @property downloadId リポジトリ上のバージョン識別子
 * @property fileName 配置されたJARのファイル名
 * @property url ダウンロード元URL
 * @property sha256 ダウンロード時に記録したsha256ハッシュ
 */
@Serializable
data class PluginDownloadResponse(
    val downloadId: String,
    val fileName: String?,
    val url: String?,
    val sha256: String?
) {
    companion object {
        /**
         * [MetadataDownloadInfoDto] から変換する
         */
        fun from(dto: MetadataDownloadInfoDto): PluginDownloadResponse =
            PluginDownloadResponse(
                downloadId = dto.downloadId,
                fileName = dto.fileName,
                url = dto.url,
                sha256 = dto.sha256
            )
    }
}