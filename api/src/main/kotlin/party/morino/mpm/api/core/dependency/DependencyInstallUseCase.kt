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
data class DependencyInstallResult(
    val availablePlugins: List<String>,
    val alreadyInstalledPlugins: List<String>,
    val unavailablePlugins: Map<String, String>
)

/**
 * プラグインの依存関係を解決（検索）するUseCase
 *
 * このUseCaseは依存関係をリポジトリで検索し、インストール可能かどうかを判定します。
 * 実際のインストールは行いません。インストールには `mpm add <plugin>` を使用してください。
 */
interface DependencyInstallUseCase {
    /**
     * 指定されたプラグインの依存関係を解決する
     *
     * 不足している依存関係をリポジトリで検索し、インストール可能かどうかを判定します。
     * 実際のインストールは行われません。
     *
     * @param pluginName プラグイン名
     * @param includeSoftDependencies softDependも含めるかどうか
     * @return 成功時はDependencyInstallResult（解決結果）、失敗時はDependencyError
     */
    suspend fun installDependencies(
        pluginName: String,
        includeSoftDependencies: Boolean = false
    ): Either<DependencyError, DependencyInstallResult>
}