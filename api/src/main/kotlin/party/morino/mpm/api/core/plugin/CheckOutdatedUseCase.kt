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
import party.morino.mpm.api.model.plugin.OutdatedInfo

/**
 * mpm outdated/outdatedAllコマンドに関するユースケース
 * プラグインの更新を確認する
 */
interface CheckOutdatedUseCase {
    /**
     * 指定されたプラグインの更新を確認する
     *
     * @param pluginName プラグイン名
     * @return 成功時は更新情報、失敗時はエラーメッセージ
     */
    suspend fun checkOutdated(pluginName: String): Either<String, OutdatedInfo>

    /**
     * すべての管理下プラグインの更新を確認する
     *
     * @return 成功時は更新情報のリスト、失敗時はエラーメッセージ
     */
    suspend fun checkAllOutdated(): Either<String, List<OutdatedInfo>>
}