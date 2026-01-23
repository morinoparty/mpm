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

package party.morino.mpm.application.dependency

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.application.dependency.DependencyResolveResult
import party.morino.mpm.api.application.dependency.DependencyService
import party.morino.mpm.api.domain.dependency.DependencyAnalyzer
import party.morino.mpm.api.domain.repository.RepositoryManager
import party.morino.mpm.api.model.dependency.DependencyError
import party.morino.mpm.api.model.dependency.DependencyInfo
import party.morino.mpm.api.model.dependency.DependencyTree

/**
 * 依存関係管理を行うApplication Service実装
 *
 * DependencyAnalyzerとDependencyInstallUseCaseImplのロジックを統合
 */
class DependencyServiceImpl :
    DependencyService,
    KoinComponent {
    // Koinによる依存性注入
    private val dependencyAnalyzer: DependencyAnalyzer by inject()
    private val repositoryManager: RepositoryManager by inject()

    /**
     * プラグインの依存関係ツリーを構築する
     *
     * DependencyAnalyzerに委譲
     */
    override fun buildDependencyTree(
        pluginName: String,
        includeSoftDependencies: Boolean
    ): Either<DependencyError, DependencyTree> =
        dependencyAnalyzer.buildDependencyTree(pluginName, includeSoftDependencies)

    /**
     * 不足している依存関係をチェックする
     *
     * DependencyAnalyzerに委譲
     */
    override fun checkMissingDependencies(pluginName: String?): Map<String, List<String>> =
        dependencyAnalyzer.checkMissingDependencies(pluginName)

    /**
     * 逆依存関係を取得する
     *
     * DependencyAnalyzerに委譲
     */
    override fun getReverseDependencies(pluginName: String): List<String> =
        dependencyAnalyzer.getReverseDependencies(pluginName)

    /**
     * プラグインの依存関係情報を取得する
     *
     * DependencyAnalyzerに委譲
     */
    override fun getDependencyInfo(pluginName: String): Either<DependencyError, DependencyInfo> =
        dependencyAnalyzer.getDependencyInfo(pluginName)

    /**
     * 指定されたプラグインの依存関係を解決する
     *
     * DependencyInstallUseCaseImplから移行したロジック
     * 不足している依存関係をリポジトリで検索し、インストール可能かどうかを判定する。
     * 実際のインストールは行わない。
     */
    override suspend fun resolveDependencies(
        pluginName: String,
        includeSoftDependencies: Boolean
    ): Either<DependencyError, DependencyResolveResult> {
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

        // 全ての依存関係が満たされている場合
        if (missingDeps.isEmpty()) {
            return DependencyResolveResult(
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
                unavailablePlugins[depName] =
                    "リポジトリに見つかりません。'mpm create-repo $depName <URL>' で追加してください"
                continue
            }

            // リポジトリに存在する場合は、mpm addでインストール可能
            availablePlugins.add(depName)
        }

        return DependencyResolveResult(
            availablePlugins = availablePlugins,
            alreadyInstalledPlugins = alreadyInstalledPlugins,
            unavailablePlugins = unavailablePlugins
        ).right()
    }
}