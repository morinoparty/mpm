/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth.model.job

import kotlinx.serialization.Serializable
import party.morino.mpm.api.application.model.job.JobProgressEntry
import java.time.format.DateTimeFormatter

/**
 * 進捗ログ1行のレスポンス
 *
 * @property timestamp 記録時刻（ISO-8601, UTC）
 * @property text MiniMessageタグを除去した平文。Webクライアントはこちらを表示する
 * @property raw サービス層が発行したMiniMessage形式の原文（色付きで表示したい場合に使う）
 */
@Serializable
data class JobProgressEntryResponse(
    val timestamp: String,
    val text: String,
    val raw: String
) {
    companion object {
        /**
         * ドメインモデルからレスポンスDTOを生成する
         *
         * @param entry 進捗ログのエントリ
         * @return レスポンスDTO
         */
        fun from(entry: JobProgressEntry): JobProgressEntryResponse =
            JobProgressEntryResponse(
                timestamp = DateTimeFormatter.ISO_INSTANT.format(entry.timestamp),
                text = entry.text,
                raw = entry.raw
            )
    }
}