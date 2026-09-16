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
import party.morino.mpm.api.application.model.file.JarDeletionResult

/**
 * JARファイル削除結果レスポンス
 */
@Serializable
data class JarDeleteResponse(
    // 削除対象のファイル名（plugins/ からの相対）
    val fileName: String,
    // true の場合はまだディスク上に残っており、サーバー停止時に削除される
    val deferred: Boolean,
    // 処理結果メッセージ
    val message: String
) {
    companion object {
        /**
         * アプリケーション層の結果からレスポンスを生成する
         */
        fun from(result: JarDeletionResult): JarDeleteResponse =
            JarDeleteResponse(
                fileName = result.fileName,
                deferred = result.deferred,
                message =
                    if (result.deferred) {
                        "File '${result.fileName}' is in use and will be deleted when the server stops."
                    } else {
                        "File '${result.fileName}' deleted successfully."
                    }
            )
    }
}