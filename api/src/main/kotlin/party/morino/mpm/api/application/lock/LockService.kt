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

package party.morino.mpm.api.application.lock

import arrow.core.Either
import party.morino.mpm.api.domain.project.lock.MpmLock
import party.morino.mpm.api.shared.error.MpmError

/**
 * ロックファイル（mpm-lock.yaml）の生成・取得を担当するサービス
 *
 * 管理下プラグインのメタデータを集約してロックファイルを再生成する。
 * install/update/add などの状態変更後に呼び出すことで、ロックファイルを
 * 実際のインストール状態に追従させる。
 */
interface LockService {
    /**
     * 管理下プラグインのメタデータからロックファイルを再生成して保存する
     *
     * @return 生成したロック内容、失敗時はエラー
     */
    suspend fun regenerate(): Either<MpmError, MpmLock>

    /**
     * 現在のロックファイルを取得する
     *
     * @return ロック内容、存在しない場合はnull
     */
    suspend fun find(): MpmLock?
}