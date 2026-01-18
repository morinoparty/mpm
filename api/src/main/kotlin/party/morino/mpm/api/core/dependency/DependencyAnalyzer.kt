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

package party.morino.mpm.api.core.dependency

import arrow.core.Either
import party.morino.mpm.api.model.dependency.DependencyError
import party.morino.mpm.api.model.dependency.DependencyInfo
import party.morino.mpm.api.model.dependency.DependencyTree

/**
 * プラグインの依存関係を解析するインターフェース
 */
interface DependencyAnalyzer {
    /**
     * 指定されたプラグインの依存関係情報を取得する
     *
     * @param pluginName プラグイン名
     * @return 成功時はDependencyInfo、失敗時はDependencyError
     */
    fun getDependencyInfo(pluginName: String): Either<DependencyError, DependencyInfo>

    /**
     * 指定されたプラグインの依存関係ツリーを構築する
     *
     * @param pluginName プラグイン名
     * @param includeSoftDependencies softDependも含めるかどうか
     * @return 成功時はDependencyTree、失敗時はDependencyError
     */
    fun buildDependencyTree(
        pluginName: String,
        includeSoftDependencies: Boolean = false
    ): Either<DependencyError, DependencyTree>

    /**
     * 不足している依存関係をチェックする
     *
     * @param pluginName プラグイン名（nullの場合は全プラグインをチェック）
     * @return プラグイン名と不足している依存関係のマップ
     */
    fun checkMissingDependencies(pluginName: String? = null): Map<String, List<String>>

    /**
     * 指定されたプラグインに依存しているプラグイン（逆依存）を取得する
     *
     * @param pluginName プラグイン名
     * @return このプラグインに依存しているプラグインのリスト
     */
    fun getReverseDependencies(pluginName: String): List<String>

    /**
     * すべてのインストール済みプラグインの依存関係情報を取得する
     *
     * @return プラグイン名とDependencyInfoのマップ
     */
    fun getAllDependencyInfo(): Map<String, DependencyInfo>
}