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
import party.morino.mpm.api.model.plugin.Plugin
import party.morino.mpm.api.model.plugin.RepositoryPlugin

/**
 * プラグインのライフサイクル管理インターフェース
 * プラグインの追加、削除、インストール、アンインストールを担当
 */
interface PluginLifecycleManager {
    /**
     * プラグインを管理対象に追加する
     * mpm.jsonのpluginsマップにプラグインを追加する
     *
     * @param plugin リポジトリプラグイン
     * @param version バージョン文字列（デフォルトは"latest"）
     * @return 成功時はUnit、失敗時はエラーメッセージ
     */
    suspend fun add(
        plugin: RepositoryPlugin,
        version: String = "latest"
    ): Either<String, Unit>

    /**
     * プラグインを管理対象から除外する（ファイルは削除されない）
     * mpm.jsonから削除するが、pluginsディレクトリからJARファイルは削除しない
     *
     * @param plugin インストール済みプラグイン
     * @return 成功時はUnit、失敗時はエラーメッセージ
     */
    suspend fun remove(plugin: InstalledPlugin): Either<String, Unit>

    /**
     * 単一のプラグインをインストールする
     *
     * @param plugin リポジトリプラグインまたはインストール済みプラグイン
     * @return 成功時はインストール結果、失敗時はエラーメッセージ
     */
    suspend fun install(plugin: Plugin): Either<String, InstallResult>

    /**
     * プラグインをアンインストールする（設定から削除し、ファイルも削除）
     * mpm.jsonから削除し、pluginsディレクトリからJARファイルも削除する
     *
     * @param plugin インストール済みプラグイン
     * @return 成功時はUnit、失敗時はエラーメッセージ
     */
    suspend fun uninstall(plugin: InstalledPlugin): Either<String, Unit>

    /**
     * mpm管理下にないプラグインを削除する
     * mpm.jsonに含まれていないプラグインのJARファイルを削除する
     *
     * @return 成功時は削除されたプラグイン名のリスト、失敗時はエラーメッセージ
     */
    suspend fun removeUnmanaged(): Either<String, List<String>>
}