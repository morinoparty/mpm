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
import party.morino.mpm.api.domain.plugin.model.VersionSpecifierParser

/**
 * 更新が必要なプラグインを、mpm.jsonのバージョン指定とロック状態で分類する
 *
 * スケジューラ本体はBukkit依存でテストしづらいため、
 * 判定ロジックだけを外部依存の無い純粋関数として切り出している
 */
object UpdateCandidateClassifier {
    /**
     * 更新が必要なプラグインを6分類する
     *
     * 判定の優先順位:
     * 1. ロック状態が判定できない（メタデータ読み込み失敗） -> unknown
     * 2. sync:指定 -> ロック中なら lockedSync、そうでなければ syncFollower
     * 3. ロック中 -> locked（指定の種類に関わらず更新しない）
     * 4. 動的指定 (latest / tag:) -> autoUpdate
     * 5. それ以外（Fixed / pattern: / mpm.json未記載） -> checkOnly
     *
     * @param needsUpdate 更新が必要と判定されたプラグインの一覧
     * @param specs mpm.jsonの「プラグイン名 -> バージョン指定文字列」マップ
     * @param lockStateOf プラグイン名からロック状態を解決する関数
     * @return 6分類の結果
     */
    fun classify(
        needsUpdate: List<OutdatedInfo>,
        specs: Map<String, String>,
        lockStateOf: (String) -> LockState
    ): UpdateCandidateClassification {
        val autoUpdate = mutableListOf<OutdatedInfo>()
        val syncFollower = mutableListOf<OutdatedInfo>()
        val lockedSync = mutableListOf<OutdatedInfo>()
        val checkOnly = mutableListOf<OutdatedInfo>()
        val locked = mutableListOf<OutdatedInfo>()
        val unknown = mutableListOf<OutdatedInfo>()

        for (info in needsUpdate) {
            val lockState = lockStateOf(info.pluginName)
            // メタデータが読めない場合は誤って更新対象にしないようunknownに隔離する
            if (lockState == LockState.UNKNOWN) {
                unknown.add(info)
                continue
            }
            val isLocked = lockState == LockState.LOCKED
            // mpm.jsonに記載が無い場合は自動更新の駆動要因を持たないものとして扱う
            val spec = specs[info.pluginName]

            when {
                // sync:は親の更新に連動するだけで、自身は駆動要因を持たない
                spec != null && VersionSpecifierParser.isSyncFormat(spec) ->
                    if (isLocked) lockedSync.add(info) else syncFollower.add(info)
                // lockは唯一の拒否権。指定の種類に関わらず更新対象から外す
                isLocked -> locked.add(info)
                // latest / tag: だけが自動更新の駆動要因となる
                spec != null && VersionSpecifierParser.isDynamic(spec) -> autoUpdate.add(info)
                // 固定バージョンやpattern:はチェックのみ
                else -> checkOnly.add(info)
            }
        }

        return UpdateCandidateClassification(
            autoUpdate = autoUpdate,
            syncFollower = syncFollower,
            lockedSync = lockedSync,
            checkOnly = checkOnly,
            locked = locked,
            unknown = unknown
        )
    }
}