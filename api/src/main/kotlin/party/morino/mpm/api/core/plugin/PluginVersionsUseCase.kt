/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.core.plugin

import arrow.core.Either

/**
 * mpm versionsコマンドに関するユースケース
 * プラグインの利用可能なバージョン一覧を取得する
 */
interface PluginVersionsUseCase {
    /**
     * 指定されたプラグインの利用可能なバージョン一覧を取得する
     *
     * @param pluginName プラグイン名
     * @return 成功時はバージョンのリスト、失敗時はエラーメッセージ
     */
    suspend fun getVersions(pluginName: String): Either<String, List<String>>
}