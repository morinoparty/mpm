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

import party.morino.mpm.api.application.model.UpdateResult

/**
 * 非同期ジョブの成功結果
 *
 * ジョブの種別ごとに結果の型が異なるため、`Any?` で持たずにsealed interfaceで表す。
 * 新しい [JobType] を追加するときは、対応する実装をここに追加する。
 */
sealed interface JobResult {
    /**
     * [JobType.UPDATE_ALL] の結果
     *
     * @property results 各プラグインの更新結果（同期実行時のレスポンスと同じ内容）
     */
    data class UpdateAll(
        val results: List<UpdateResult>
    ) : JobResult
}