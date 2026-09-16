/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth.model.file

import kotlinx.serialization.Serializable
import party.morino.mpm.api.application.model.file.JarFileInfo
import java.time.format.DateTimeFormatter

/**
 * plugins/ 直下のJARファイル1件のレスポンス
 *
 * @property fileName ファイル名（plugins/ からの相対。`DELETE /jars/{fileName}` にそのまま渡せる）
 * @property sizeBytes ファイルサイズ（バイト）
 * @property lastModified 最終更新日時（ISO-8601, UTC）
 * @property pluginName plugin.yml から読み取ったプラグイン名（読めないJARは null）
 * @property pluginVersion plugin.yml から読み取ったバージョン（読めないJARは null）
 * @property running 実行中の mpm 自身のJARなら true
 * @property pendingDeletion サーバー停止時の削除が予約済みなら true
 */
@Serializable
data class JarFileResponse(
    val fileName: String,
    val sizeBytes: Long,
    val lastModified: String,
    val pluginName: String?,
    val pluginVersion: String?,
    val running: Boolean,
    val pendingDeletion: Boolean
) {
    companion object {
        /**
         * アプリケーション層のモデルからレスポンスを生成する
         */
        fun from(info: JarFileInfo): JarFileResponse =
            JarFileResponse(
                fileName = info.fileName,
                sizeBytes = info.sizeBytes,
                lastModified = DateTimeFormatter.ISO_INSTANT.format(info.lastModified),
                pluginName = info.pluginName,
                pluginVersion = info.pluginVersion,
                running = info.running,
                pendingDeletion = info.pendingDeletion
            )
    }
}