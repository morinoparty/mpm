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

package party.morino.mpm.application.health

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.right
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.application.dependency.DependencyService
import party.morino.mpm.api.application.health.DoctorReport
import party.morino.mpm.api.application.health.DoctorService
import party.morino.mpm.api.application.lock.LockService
import party.morino.mpm.api.application.model.PluginFilter
import party.morino.mpm.api.application.model.verify.VerifyStatus
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.domain.plugin.model.PluginSpec
import party.morino.mpm.api.domain.project.repository.ProjectRepository
import party.morino.mpm.api.shared.error.MpmError
import kotlin.coroutines.cancellation.CancellationException

/**
 * [DoctorService] の実装
 *
 * 既存の各サービスをKoinで注入し、それらを組み合わせて診断レポートを生成する。
 */
class DoctorServiceImpl :
    DoctorService,
    KoinComponent {
    // Koinによる依存性注入
    private val projectRepository: ProjectRepository by inject()
    private val infoService: PluginInfoService by inject()
    private val dependencyService: DependencyService by inject()
    private val lockService: LockService by inject()

    override suspend fun diagnose(): Either<MpmError, DoctorReport> {
        // プロジェクトが未初期化の場合は診断自体が行えないためエラーを返す
        val project = projectRepository.findOrError().getOrElse { return Either.Left(it) }

        val warnings = mutableListOf<String>()

        // 管理下（Managed）のプラグイン名一覧
        val managedNames =
            project.plugins
                .filterValues { it is PluginSpec.Managed }
                .keys
                .map { it.value }

        // 各チェックは独立して実行し、1つの失敗（例外含む）が全体を止めないようにする。
        // 失敗は warnings に集約して診断を継続する（warnings は「異常」判定には含めない）。

        // 1. 不足している必須依存関係（プラグイン名 → 不足依存リスト）
        val missingDependencies =
            runDiagnostic("依存関係チェック", warnings, emptyMap()) {
                dependencyService.checkMissingDependencies().filterValues { it.isNotEmpty() }
            }

        // 2. 整合性（ハッシュ不一致 / JAR欠落）
        val verifyEntries =
            runDiagnostic("整合性チェック", warnings, emptyList()) {
                infoService.verifyInstalled().getOrElse {
                    warnings.add("整合性チェックに失敗しました: ${it.message}")
                    emptyList()
                }
            }
        val hashMismatches = verifyEntries.filter { it.status == VerifyStatus.MISMATCH }.map { it.pluginName }
        val fileMissing = verifyEntries.filter { it.status == VerifyStatus.FILE_MISSING }.map { it.pluginName }

        // 3. 管理外（orphan）プラグイン
        val unmanagedPlugins =
            runDiagnostic("管理外プラグインの確認", warnings, emptyList()) {
                infoService.list(PluginFilter.UNMANAGED).map { it.name.value }
            }

        // 4. 更新可能なプラグイン
        val outdatedPlugins =
            runDiagnostic("更新チェック", warnings, emptyList()) {
                infoService.checkAllOutdated().fold(
                    {
                        warnings.add("更新チェックに失敗しました: ${it.message}")
                        emptyList()
                    },
                    { result ->
                        // 個別プラグインのチェックエラーは警告として記録する
                        result.errors.forEach { warnings.add("${it.pluginName}: ${it.errorMessage}") }
                        result.outdatedPlugins.filter { it.needsUpdate }
                    }
                )
            }

        // 5. ロックファイルのドリフト
        val lock = lockService.find()
        val missingFromLock: List<String>
        val staleLockEntries: List<String>
        if (lock == null) {
            // ロック未生成。管理下プラグインがあるのに未生成なら警告する
            missingFromLock = emptyList()
            staleLockEntries = emptyList()
            if (managedNames.isNotEmpty()) {
                warnings.add("mpm-lock.yaml が未生成です。'mpm install' で生成できます。")
            }
        } else {
            val lockKeys = lock.plugins.keys
            // 管理下だがロックに無い（ドリフト）
            missingFromLock = managedNames.filter { m -> lockKeys.none { it.equals(m, ignoreCase = true) } }
            // ロックにあるが管理下でない（ドリフト）
            staleLockEntries = lockKeys.filter { k -> managedNames.none { it.equals(k, ignoreCase = true) } }
        }

        return DoctorReport(
            missingDependencies = missingDependencies,
            hashMismatches = hashMismatches,
            fileMissing = fileMissing,
            unmanagedPlugins = unmanagedPlugins,
            outdatedPlugins = outdatedPlugins,
            missingFromLock = missingFromLock,
            staleLockEntries = staleLockEntries,
            warnings = warnings
        ).right()
    }

    /**
     * 1つの診断チェックを実行し、想定外の例外は警告に変換してデフォルト値を返す
     *
     * 1チェックの失敗が診断全体を止めないようにするための防御的ラッパー。
     * コルーチンのキャンセルは握り潰さず伝播させる。
     *
     * @param label 失敗時のメッセージに使うチェック名
     * @param warnings 失敗メッセージを追記する警告リスト
     * @param default 失敗時に返すデフォルト値
     * @param block 実行するチェック本体
     */
    private inline fun <T> runDiagnostic(
        label: String,
        warnings: MutableList<String>,
        default: T,
        block: () -> T
    ): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            warnings.add("$label に失敗しました: ${e.message}")
            default
        }
}