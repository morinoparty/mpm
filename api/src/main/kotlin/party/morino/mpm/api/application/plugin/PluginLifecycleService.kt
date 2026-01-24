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
import party.morino.mpm.api.application.model.AddWithDependenciesResult
import party.morino.mpm.api.application.model.InstallResult
import party.morino.mpm.api.domain.plugin.model.ManagedPlugin
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.shared.error.MpmError

/**
 * プラグインのライフサイクル管理サービス
 *
 * プラグインの追加・削除・インストール・アンインストールを担当する
 * 薄いファサードとして機能し、オーケストレーションのみを行う
 */
interface PluginLifecycleService {
    /**
     * プラグインを追加する
     *
     * mpm.jsonに依存関係を追加し、メタデータを取得・保存する
     *
     * @param name プラグイン名（例: "modrinth:bluemap"）
     * @param version バージョン指定
     * @return 追加されたプラグイン情報
     */
    suspend fun add(
        name: PluginName,
        version: VersionSpecifier
    ): Either<MpmError, ManagedPlugin>

    /**
     * プラグインを削除する
     *
     * mpm.jsonから依存関係を削除し、メタデータを削除する
     * プラグインファイル自体は削除しない
     *
     * @param name プラグイン名
     * @return 成功時はUnit
     */
    suspend fun remove(name: PluginName): Either<MpmError, Unit>

    /**
     * プラグインをインストールする
     *
     * メタデータに基づいてプラグインファイルをダウンロード・配置する
     *
     * @param name プラグイン名
     * @return インストール結果
     */
    suspend fun install(name: PluginName): Either<MpmError, InstallResult>

    /**
     * プラグインをアンインストールする
     *
     * プラグインファイルを削除する（メタデータは保持）
     *
     * @param name プラグイン名
     * @return 成功時はUnit
     */
    suspend fun uninstall(name: PluginName): Either<MpmError, Unit>

    /**
     * 管理外プラグインを削除する
     *
     * MPMで管理されていないプラグインファイルをすべて削除する
     *
     * @return 削除されたプラグイン数
     */
    suspend fun removeUnmanaged(): Either<MpmError, Int>

    /**
     * プラグインを依存関係と共に追加・インストールする
     *
     * リポジトリファイルのdependenciesを再帰的に解決し、
     * すべての依存プラグインを追加・インストールする
     *
     * @param name プラグイン名
     * @param version バージョン指定
     * @param includeSoftDependencies softDependenciesも含めるかどうか
     * @return 追加結果（追加されたプラグイン、スキップされたプラグイン、失敗したプラグイン）
     */
    suspend fun addWithDependencies(
        name: PluginName,
        version: VersionSpecifier,
        includeSoftDependencies: Boolean = false
    ): Either<MpmError, AddWithDependenciesResult>
}