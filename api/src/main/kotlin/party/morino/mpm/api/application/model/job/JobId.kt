/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
 * and related and neighboring rights to this software to the public domain worldwide.
 * This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.application.model.job

import java.util.UUID

/**
 * 非同期ジョブの識別子
 *
 * HTTPクライアントは `POST /jobs` で受け取ったこの値を使って `GET /jobs/{id}` を
 * ポーリングし、処理の進捗と完了を知る。
 *
 * @property value ジョブID文字列（UUID形式）
 */
@JvmInline
value class JobId(
    val value: String
) {
    companion object {
        /**
         * 新しいジョブIDを発行する
         *
         * 推測されにくく衝突しない値であればよいためUUIDを用いる。
         *
         * @return 新規発行したジョブID
         */
        fun generate(): JobId = JobId(UUID.randomUUID().toString())
    }
}