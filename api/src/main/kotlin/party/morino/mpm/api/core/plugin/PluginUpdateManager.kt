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
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.api.model.plugin.UpdateResult

/**
 * プラグイン更新管理インターフェース
 * プラグインの更新、ロック、一括インストールを担当
 */
interface PluginUpdateManager {
    /**
     * 新しいバージョンがあるすべてのプラグインを更新する
     *
     * @return 成功時は更新結果のリスト、失敗時はエラーメッセージ
     */
    suspend fun update(): Either<String, List<UpdateResult>>

    /**
     * プラグインをロックして自動更新を防ぐ
     * プラグインのメタデータにロックフラグを設定する
     *
     * @param plugin インストール済みプラグイン
     * @return 成功時はUnit、失敗時はエラーメッセージ
     */
    suspend fun lock(plugin: InstalledPlugin): Either<String, Unit>

    /**
     * プラグインのロックを解除する
     * プラグインのメタデータからロックフラグを削除する
     *
     * @param plugin インストール済みプラグイン
     * @return 成功時はUnit、失敗時はエラーメッセージ
     */
    suspend fun unlock(plugin: InstalledPlugin): Either<String, Unit>

    /**
     * mpm.jsonに定義されたすべてのプラグインを一括インストールする
     * metadataファイルが存在しないか、バージョンが異なるプラグインのみインストールする
     *
     * @return 成功時はインストール結果、失敗時はエラーメッセージ
     */
    suspend fun installAll(): Either<String, BulkInstallResult>
}