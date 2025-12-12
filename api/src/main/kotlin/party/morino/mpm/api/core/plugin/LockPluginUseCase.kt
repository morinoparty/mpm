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
 * mpm lockコマンドに関するユースケース
 * プラグインをロックして自動更新を防ぐ
 */
interface LockPluginUseCase {
    /**
     * プラグインをロックする
     * プラグインのメタデータにロックフラグを設定する
     *
     * @param pluginName プラグイン名
     * @return 成功時はUnit、失敗時はエラーメッセージ
     */
    suspend fun lockPlugin(pluginName: String): Either<String, Unit>
}