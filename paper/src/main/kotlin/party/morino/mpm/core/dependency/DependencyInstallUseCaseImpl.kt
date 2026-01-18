/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.core.dependency

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.core.dependency.DependencyAnalyzer
import party.morino.mpm.api.core.dependency.DependencyInstallResult
import party.morino.mpm.api.core.dependency.DependencyInstallUseCase
import party.morino.mpm.api.core.repository.RepositoryManager
import party.morino.mpm.api.model.dependency.DependencyError

/**
 * プラグインの依存関係を解決（検索）するUseCaseの実装
 *
 * 不足している依存関係を検出し、リポジトリで検索してインストール可能かどうかを判定する。
 * 実際のインストールは行わない。
 */
class DependencyInstallUseCaseImpl :
    DependencyInstallUseCase,
    KoinComponent {
    // Koinによる依存性注入
    private val dependencyAnalyzer: DependencyAnalyzer by inject()
    private val repositoryManager: RepositoryManager by inject()

    /**
     * 指定されたプラグインの依存関係を解決する
     *
     * 不足している依存関係をリポジトリで検索し、インストール可能かどうかを判定する。
     * 実際のインストールは行わない。
     *
     * @param pluginName プラグイン名
     * @param includeSoftDependencies softDependも含めるかどうか
     * @return 成功時はDependencyInstallResult（解決結果）、失敗時はDependencyError
     */
    override suspend fun installDependencies(
        pluginName: String,
        includeSoftDependencies: Boolean
    ): Either<DependencyError, DependencyInstallResult> {
        // 依存関係ツリーを構築
        val tree =
            dependencyAnalyzer
                .buildDependencyTree(pluginName, includeSoftDependencies)
                .getOrElse { return it.left() }

        // 不足している依存関係を取得
        val missingDeps = tree.missingRequired.toMutableList()
        if (includeSoftDependencies) {
            missingDeps.addAll(tree.missingSoft)
        }

        if (missingDeps.isEmpty()) {
            // 全ての依存関係が満たされている場合
            return DependencyInstallResult(
                availablePlugins = emptyList(),
                alreadyInstalledPlugins = emptyList(),
                unavailablePlugins = emptyMap()
            ).right()
        }

        val availablePlugins = mutableListOf<String>()
        val alreadyInstalledPlugins = mutableListOf<String>()
        val unavailablePlugins = mutableMapOf<String, String>()

        // 各依存関係をリポジトリで検索
        for (depName in missingDeps.distinct()) {
            // リポジトリでプラグインを検索
            val repositoryFile = repositoryManager.getRepositoryFile(depName)

            if (repositoryFile == null) {
                // リポジトリに見つからない場合
                unavailablePlugins[depName] = "リポジトリに見つかりません。'mpm create-repo $depName <URL>' で追加してください"
                continue
            }

            // リポジトリに存在する場合は、mpm addでインストール可能
            availablePlugins.add(depName)
        }

        return DependencyInstallResult(
            availablePlugins = availablePlugins,
            alreadyInstalledPlugins = alreadyInstalledPlugins,
            unavailablePlugins = unavailablePlugins
        ).right()
    }
}