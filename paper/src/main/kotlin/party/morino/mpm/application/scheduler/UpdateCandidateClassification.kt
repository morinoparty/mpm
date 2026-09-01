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
 * cron実行時の更新候補の分類結果
 *
 * バージョン指定が「自動更新の駆動要因」、lockが「唯一の拒否権」という
 * 二軸で更新対象を決定する。
 *
 * @property autoUpdate 動的指定(latest / tag:)かつ非ロック。実際にダウンロード更新する対象
 * @property syncFollower sync:指定かつ非ロック。親が更新された場合のみ追従更新される（駆動要因は持たない）
 * @property lockedSync sync:指定かつロック中。親が更新されても更新しない
 * @property checkOnly 固定バージョン / pattern: などの非動的指定かつ非ロック。報告のみで更新しない
 * @property locked ロック中（指定の種類に関わらず更新しない）。更新があることは報告する
 * @property unknown メタデータ読み込みに失敗し、ロック状態を判定できなかったもの
 */
data class UpdateCandidateClassification(
    val autoUpdate: List<OutdatedInfo>,
    val syncFollower: List<OutdatedInfo>,
    val lockedSync: List<OutdatedInfo>,
    val checkOnly: List<OutdatedInfo>,
    val locked: List<OutdatedInfo>,
    val unknown: List<OutdatedInfo>
) {
    /** 更新が必要と判定されたプラグインが1件も無いか */
    val isEmpty: Boolean
        get() =
            autoUpdate.isEmpty() &&
                syncFollower.isEmpty() &&
                lockedSync.isEmpty() &&
                checkOnly.isEmpty() &&
                locked.isEmpty() &&
                unknown.isEmpty()
}