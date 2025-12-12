/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
and related and neighboring rights to this software to the public domain worldwide.
This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.core.plugin

import arrow.core.Either

/**
 * mpm removeUnmanagedコマンドに関するユースケース
 * mpm管理下にないプラグインを削除する
 */
interface RemoveUnmanagedUseCase {
    /**
     * mpm管理下にないプラグインを削除する
     * mpm.jsonに含まれていないプラグインのJARファイルを削除する
     *
     * @return 成功時は削除されたプラグイン名のリスト、失敗時はエラーメッセージ
     */
    suspend fun removeUnmanaged(): Either<String, List<String>>
}