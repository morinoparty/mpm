/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.scheduler

import party.morino.mpm.api.application.model.outdated.OutdatedInfo

/**
 * 定期チェックの分類結果から、外部へ通知すべきものを選ぶ
 *
 * 通知したいのは「mpmが自律的に行ったチェックで、mpm自身では解消できない更新可能性を
 * 新しく検知したもの」に限る。そのため次の2段で絞り込む。
 *
 * 1. 分類による絞り込み（mpmが自動で解消するものは通知しない）
 * 2. [OutdatedInfo.latestChanged] による絞り込み
 *    （更新可能な状態が続いているだけのものを毎回鳴らさない）
 *
 * 外部依存を持たない純粋関数にしてあるので、通知対象の方針変更はこのファイルだけで完結する。
 */
internal object NotifiableOutdatedSelector {
    /**
     * 通知対象を選ぶ
     *
     * @param classification 分類結果
     * @param failedAutoUpdates 自動更新を試みて失敗したプラグイン名
     *   （mpmが試みて直せなかったものなので、更新成功時と違って人に知らせる必要がある）
     * @return 通知すべき更新情報
     */
    fun select(
        classification: UpdateCandidateClassification,
        failedAutoUpdates: Set<String>
    ): List<OutdatedInfo> {
        val candidates =
            buildList {
                // 自動更新に成功したものは直後に PluginUpdateEvent が飛ぶため通知しない。
                // 失敗したものだけを拾う（現状はUPDATEもOUTDATEDも飛ばず完全に沈黙している）。
                addAll(classification.autoUpdate.filter { it.pluginName in failedAutoUpdates })

                // syncFollower は親に追従するだけなので、親が別の分類で通知される。
                // ここで拾うと親子で二重に鳴る。

                // 以下はいずれも人が手を動かさない限り解消しない
                addAll(classification.checkOnly)
                addAll(classification.locked)
                addAll(classification.lockedSync)
                addAll(classification.unknown)
            }

        // 同じ内容を繰り返し鳴らさない。新しい上流リリースを検知した回だけ通す。
        return candidates.filter { it.latestChanged }
    }
}