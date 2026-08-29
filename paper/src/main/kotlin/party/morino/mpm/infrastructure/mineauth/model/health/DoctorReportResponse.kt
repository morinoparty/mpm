/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth.model.health

import kotlinx.serialization.Serializable
import party.morino.mpm.api.application.health.DoctorReport
import party.morino.mpm.infrastructure.mineauth.model.outdated.OutdatedPluginResponse

/**
 * サーバー診断結果レスポンス（`mpm doctor` 相当）
 *
 * @property hasProblems 対処が必要な問題が1つ以上あるかどうか（管理外・更新可能・警告は含まない）
 * @property missingDependencies プラグイン名 → 不足している必須依存プラグイン名の一覧
 * @property hashMismatches 整合性検証でハッシュ不一致となったプラグイン名
 * @property fileMissing メタデータはあるがJARが見つからないプラグイン名
 * @property unmanagedPlugins mpm管理外のプラグイン名
 * @property outdatedPlugins 更新が利用可能なプラグイン
 * @property missingFromLock mpm.json管理下だがmpm-lock.yamlに記録がないプラグイン名
 * @property staleLockEntries mpm-lock.yamlにあるがmpm.json管理下にないプラグイン名
 * @property warnings 診断中に発生した警告メッセージ
 */
@Serializable
data class DoctorReportResponse(
    val hasProblems: Boolean,
    val missingDependencies: Map<String, List<String>>,
    val hashMismatches: List<String>,
    val fileMissing: List<String>,
    val unmanagedPlugins: List<String>,
    val outdatedPlugins: List<OutdatedPluginResponse>,
    val missingFromLock: List<String>,
    val staleLockEntries: List<String>,
    val warnings: List<String>
) {
    companion object {
        /**
         * DoctorReportから変換する
         */
        fun from(report: DoctorReport): DoctorReportResponse =
            DoctorReportResponse(
                hasProblems = report.hasProblems,
                missingDependencies = report.missingDependencies,
                hashMismatches = report.hashMismatches,
                fileMissing = report.fileMissing,
                unmanagedPlugins = report.unmanagedPlugins,
                outdatedPlugins = report.outdatedPlugins.map { OutdatedPluginResponse.from(it) },
                missingFromLock = report.missingFromLock,
                staleLockEntries = report.staleLockEntries,
                warnings = report.warnings
            )
    }
}