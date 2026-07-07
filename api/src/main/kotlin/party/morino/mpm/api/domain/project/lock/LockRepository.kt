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

package party.morino.mpm.api.domain.project.lock

/**
 * ロックファイル（mpm-lock.yaml）の読み書きを担当するリポジトリ
 */
interface LockRepository {
    /**
     * ロックファイルを読み込む
     *
     * @return ロックファイルの内容、存在しない/パース失敗の場合はnull
     */
    suspend fun find(): MpmLock?

    /**
     * ロックファイルを保存する（アトミック書き込み）
     *
     * @param lock 保存するロック内容
     */
    suspend fun save(lock: MpmLock)

    /**
     * ロックファイルが存在するかどうかを確認する
     */
    suspend fun exists(): Boolean
}