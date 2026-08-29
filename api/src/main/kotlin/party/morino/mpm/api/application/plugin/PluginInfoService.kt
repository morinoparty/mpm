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

package party.morino.mpm.api.application.plugin

import arrow.core.Either
import party.morino.mpm.api.application.model.PluginFilter
import party.morino.mpm.api.application.model.outdated.OutdatedCheckResult
import party.morino.mpm.api.application.model.outdated.OutdatedInfo
import party.morino.mpm.api.application.model.verify.VerifyEntry
import party.morino.mpm.api.application.plugin.model.detail.PluginDetail
import party.morino.mpm.api.domain.plugin.dto.version.HistoryEntryDto
import party.morino.mpm.api.domain.plugin.model.ManagedPlugin
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.domain.plugin.model.VersionDetail
import party.morino.mpm.api.shared.error.MpmError

/**
 * プラグイン情報取得サービス
 *
 * プラグイン一覧・バージョン情報・更新確認を担当する
 * 薄いファサードとして機能し、オーケストレーションのみを行う
 */
interface PluginInfoService {
    /**
     * プラグイン一覧を取得する
     *
     * @param filter フィルタ条件
     * @return プラグイン一覧
     */
    suspend fun list(filter: PluginFilter = PluginFilter.ALL): List<ManagedPlugin>

    /**
     * 指定プラグインの利用可能なバージョン一覧を取得する
     *
     * @param name プラグイン名
     * @return バージョン一覧
     */
    suspend fun getVersions(name: PluginName): Either<MpmError, List<VersionDetail>>

    /**
     * 指定プラグインのインストール履歴を取得する
     *
     * メタデータ（metadata/<plugin>.yaml）に記録された履歴を、古い順のまま返す。
     * `mpm rollback` の切り戻し先解決や、web console の履歴表示から利用する。
     *
     * @param name プラグイン名
     * @return 履歴エントリ一覧（古い順）。メタデータが無い場合は [MpmError.PluginError.MetadataNotFound]
     */
    suspend fun getHistory(name: PluginName): Either<MpmError, List<HistoryEntryDto>>

    /**
     * 指定プラグインの更新情報を取得する
     *
     * @param name プラグイン名
     * @return 更新情報（更新不要の場合はnull）
     */
    suspend fun checkOutdated(name: PluginName): Either<MpmError, OutdatedInfo?>

    /**
     * すべてのプラグインの更新情報を取得する
     *
     * @return 更新が必要なプラグインの情報と、チェックに失敗したプラグインのエラー情報
     */
    suspend fun checkAllOutdated(): Either<MpmError, OutdatedCheckResult>

    /**
     * インストール済み管理下プラグインの整合性を再検証する
     *
     * 各プラグインのJARのsha256を計算し、メタデータに保存されたハッシュと照合する。
     * ネットワークアクセスは行わず、ローカルの検証のみを行う。
     *
     * @return プラグインごとの検証結果一覧
     */
    suspend fun verifyInstalled(): Either<MpmError, List<VerifyEntry>>

    /**
     * 指定プラグインの詳細情報を取得する（`mpm info`）
     *
     * リポジトリからプロジェクト詳細を取得し、最新バージョンとローカルのインストール状態を付加する。
     *
     * @param name プラグイン名
     * @return プラグイン詳細
     */
    suspend fun getPluginDetail(name: PluginName): Either<MpmError, PluginDetail>
}