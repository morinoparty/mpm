/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.Logger
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.downloader.model.TargetPlatform
import party.morino.mpm.api.domain.project.repository.ProjectRepository
import party.morino.mpm.api.model.plugin.PluginData
import party.morino.mpm.utils.PluginDataUtils
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * ダウンロード済みプラグインJARに対する、インストール前の共通検証ロジック
 *
 * 動作中のプラットフォームとの互換性と、必須依存関係のチェックを行う。
 * api-versionはPaperでは異なっていても動作することが多いため、ここでは検証しない。
 * PluginLifecycleServiceImpl.install() と PluginUpdateServiceImpl の更新処理の両方から呼び出され、
 * 検証ロジックが2箇所で乖離しないよう一元化している
 */
class PluginInstallValidator : KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()

    // 複数の記述子を持つJARで、このプラットフォームが読み込む記述子を優先するために使う
    private val targetPlatform: TargetPlatform by inject()
    private val projectRepository: ProjectRepository by inject()
    private val logger: Logger by inject()

    /**
     * ダウンロード済みのtempファイルに対して依存関係の事前検証を行う
     *
     * force指定時は依存不足があっても警告ログのみを出力して続行扱いとする
     *
     * @param downloadedFile ダウンロード済みのtempファイル
     * @param pluginName プラグイン名（ログ出力用）
     * @param force trueの場合、依存不足でも警告のみで続行する
     * @return 検証結果
     */
    suspend fun validate(
        downloadedFile: File,
        pluginName: String,
        force: Boolean
    ): PluginInstallValidationResult {
        // tempファイルからプラグインデータを抽出（破損JARでも例外を握りつぶしてスキップする）
        // 他のプラットフォーム向けの記述子も読み取り、下の互換性チェックで明確に拒否する
        val pluginData =
            try {
                PluginDataUtils.readAnyPluginData(downloadedFile, targetPlatform)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("Failed to read plugin data from downloaded file ($pluginName): ${e.message}")
                null
            }

        // 他のプラットフォーム向けのプラグイン（Paper 上の Velocity プラグインなど）は読み込まれないため、force でも拒否する
        if (pluginData != null && !targetPlatform.supports(pluginData)) {
            return PluginInstallValidationResult.IncompatiblePlatform(descriptorKindOf(pluginData), targetPlatform)
        }

        // 必須依存関係のチェック
        if (pluginData != null) {
            val requiredDeps =
                when (pluginData) {
                    is PluginData.BukkitPluginData -> pluginData.depend
                    is PluginData.PaperPluginData -> pluginData.depend
                    is PluginData.VelocityPluginData -> pluginData.depend
                }

            if (requiredDeps.isNotEmpty()) {
                // mpm.jsonに登録済みのプラグイン名を取得
                val managedPlugins =
                    projectRepository
                        .find()
                        ?.plugins
                        ?.keys
                        ?.map { it.value }
                        ?.toSet()
                        .orEmpty()
                // pluginsディレクトリに存在するプラグイン名を取得
                val pluginsDir = pluginDirectory.getPluginsDirectory()
                val installedPluginNames =
                    pluginsDir
                        .listFiles { f ->
                            f.isFile && f.extension == "jar"
                        }?.mapNotNull { jar ->
                            try {
                                PluginDataUtils.getPluginData(jar, targetPlatform)?.name
                            } catch (_: Exception) {
                                null
                            }
                        }?.toSet()
                        .orEmpty()

                // 管理対象とインストール済みのどちらにもない依存関係を検出
                val missingDeps =
                    requiredDeps.filter { dep ->
                        !managedPlugins.contains(dep) && !installedPluginNames.contains(dep)
                    }

                if (missingDeps.isNotEmpty()) {
                    if (!force) {
                        return PluginInstallValidationResult.MissingDependencies(missingDeps)
                    }
                    logger.warn(
                        "$pluginName: 必須依存プラグインが不足しています: " +
                            "${missingDeps.joinToString(", ")} (forced install)"
                    )
                }
            }
        }

        return PluginInstallValidationResult.Valid
    }

    /**
     * 記述子の種類を、利用者に見せるファイル名で返す
     */
    private fun descriptorKindOf(pluginData: PluginData): String =
        when (pluginData) {
            is PluginData.BukkitPluginData -> "plugin.yml"
            is PluginData.PaperPluginData -> "paper-plugin.yml"
            is PluginData.VelocityPluginData -> "velocity-plugin.json"
        }
}