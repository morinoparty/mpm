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

package party.morino.mpm.api.application.health

import party.morino.mpm.api.application.model.outdated.OutdatedInfo

/**
 * `mpm doctor` の診断結果
 *
 * 既存の各チェック（依存関係・整合性・更新・ロック・管理外）を集約したもの。
 *
 * @param missingDependencies プラグイン名 → 不足している必須依存プラグイン名のリスト
 * @param hashMismatches 整合性検証でハッシュ不一致となったプラグイン名
 * @param fileMissing メタデータはあるがJARファイルが見つからないプラグイン名
 * @param unmanagedPlugins mpm管理外（orphan）のプラグイン名
 * @param outdatedPlugins 更新が利用可能なプラグイン
 * @param missingFromLock mpm.json管理下だがmpm-lock.yamlに記録がないプラグイン名（ロックのドリフト）
 * @param staleLockEntries mpm-lock.yamlにあるがmpm.jsonの管理下にないプラグイン名（ロックのドリフト）
 * @param warnings チェック中に発生した警告・エラーメッセージ
 */
data class DoctorReport(
    val missingDependencies: Map<String, List<String>>,
    val hashMismatches: List<String>,
    val fileMissing: List<String>,
    val unmanagedPlugins: List<String>,
    val outdatedPlugins: List<OutdatedInfo>,
    val missingFromLock: List<String>,
    val staleLockEntries: List<String>,
    val warnings: List<String>
) {
    /**
     * 対処が必要な「実際の問題」が1つでもあるか
     *
     * 管理外プラグイン（[unmanagedPlugins]）・更新可能（[outdatedPlugins]）・[warnings] は判定に含めない。
     * 特に [warnings] にはネットワークの一時障害などによるチェック失敗が含まれうるため、
     * これを「異常」と扱うと健全なサーバーを誤って赤判定してしまう。判定は確定した問題のみに基づく。
     */
    val hasProblems: Boolean
        get() =
            missingDependencies.isNotEmpty() ||
                hashMismatches.isNotEmpty() ||
                fileMissing.isNotEmpty() ||
                missingFromLock.isNotEmpty() ||
                staleLockEntries.isNotEmpty()
}