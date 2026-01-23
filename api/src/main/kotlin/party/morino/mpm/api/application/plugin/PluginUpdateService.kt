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
import party.morino.mpm.api.application.model.BulkInstallResult
import party.morino.mpm.api.application.model.UpdateResult
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.shared.error.MpmError

/**
 * プラグイン更新サービス
 *
 * プラグインの更新・ロック/アンロック・一括インストールを担当する
 * 薄いファサードとして機能し、オーケストレーションのみを行う
 */
interface PluginUpdateService {
    /**
     * すべてのプラグインを更新する
     *
     * ロックされていないプラグインを最新バージョンに更新する
     *
     * @return 更新結果一覧
     */
    suspend fun update(): Either<MpmError, List<UpdateResult>>

    /**
     * 指定プラグインを更新する
     *
     * @param name プラグイン名
     * @return 更新結果
     */
    suspend fun update(name: PluginName): Either<MpmError, UpdateResult>

    /**
     * mpm.jsonに記載されたすべてのプラグインを一括インストールする
     *
     * @return 一括インストール結果
     */
    suspend fun installAll(): Either<MpmError, BulkInstallResult>

    /**
     * プラグインをロックする
     *
     * ロックされたプラグインは自動更新の対象外になる
     *
     * @param name プラグイン名
     * @return 成功時はUnit
     */
    suspend fun lock(name: PluginName): Either<MpmError, Unit>

    /**
     * プラグインのロックを解除する
     *
     * @param name プラグイン名
     * @return 成功時はUnit
     */
    suspend fun unlock(name: PluginName): Either<MpmError, Unit>
}