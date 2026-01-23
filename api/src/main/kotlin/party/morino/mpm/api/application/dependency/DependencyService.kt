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

package party.morino.mpm.api.application.dependency

import arrow.core.Either
import party.morino.mpm.api.model.dependency.DependencyError
import party.morino.mpm.api.model.dependency.DependencyInfo
import party.morino.mpm.api.model.dependency.DependencyTree

/**
 * 依存関係の解決結果を表すデータクラス
 *
 * 注意: このクラスは依存関係の「解決」（リポジトリでの検索）結果を表します。
 * 実際のインストールは `mpm add <plugin>` コマンドで別途行う必要があります。
 *
 * @property availablePlugins リポジトリで見つかった、インストール可能なプラグインのリスト
 * @property alreadyInstalledPlugins 既にインストール済みのプラグインのリスト
 * @property unavailablePlugins リポジトリに見つからなかったプラグインとエラーメッセージのマップ
 */
data class DependencyResolveResult(
    val availablePlugins: List<String>,
    val alreadyInstalledPlugins: List<String>,
    val unavailablePlugins: Map<String, String>
)

/**
 * 依存関係管理サービス
 *
 * プラグインの依存関係の解析・解決を担当する
 * 薄いファサードとして機能し、オーケストレーションのみを行う
 */
interface DependencyService {
    /**
     * プラグインの依存関係ツリーを構築する
     *
     * @param pluginName プラグイン名
     * @param includeSoftDependencies softDependも含めるかどうか
     * @return 依存関係ツリー
     */
    fun buildDependencyTree(
        pluginName: String,
        includeSoftDependencies: Boolean = false
    ): Either<DependencyError, DependencyTree>

    /**
     * 不足している依存関係をチェックする
     *
     * @param pluginName プラグイン名（nullの場合は全プラグイン）
     * @return プラグイン名 -> 不足している依存関係のリスト
     */
    fun checkMissingDependencies(pluginName: String? = null): Map<String, List<String>>

    /**
     * 逆依存関係を取得する（指定プラグインに依存しているプラグインのリスト）
     *
     * @param pluginName プラグイン名
     * @return 逆依存関係のリスト
     */
    fun getReverseDependencies(pluginName: String): List<String>

    /**
     * プラグインの依存関係情報を取得する
     *
     * @param pluginName プラグイン名
     * @return 依存関係情報
     */
    fun getDependencyInfo(pluginName: String): Either<DependencyError, DependencyInfo>

    /**
     * 指定されたプラグインの依存関係を解決する
     *
     * 不足している依存関係をリポジトリで検索し、インストール可能かどうかを判定する。
     * 実際のインストールは行わない。
     *
     * @param pluginName プラグイン名
     * @param includeSoftDependencies softDependも含めるかどうか
     * @return 成功時はDependencyResolveResult（解決結果）、失敗時はDependencyError
     */
    suspend fun resolveDependencies(
        pluginName: String,
        includeSoftDependencies: Boolean = false
    ): Either<DependencyError, DependencyResolveResult>
}