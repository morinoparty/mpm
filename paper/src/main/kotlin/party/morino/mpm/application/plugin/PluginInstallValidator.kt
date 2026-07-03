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

package party.morino.mpm.application.plugin

import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.domain.compatibility.ApiVersionChecker
import party.morino.mpm.api.domain.compatibility.CompatibilityResult
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.project.repository.ProjectRepository
import party.morino.mpm.api.model.plugin.PluginData
import party.morino.mpm.utils.PluginDataUtils
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * ダウンロード済みプラグインJARに対する、インストール前の共通検証ロジック
 *
 * APIバージョンの互換性チェックと必須依存関係のチェックを行う。
 * PluginLifecycleServiceImpl.install() と PluginUpdateServiceImpl の更新処理の両方から呼び出され、
 * 検証ロジックが2箇所で乖離しないよう一元化している
 */
class PluginInstallValidator : KoinComponent {
    // Koinによる依存性注入
    private val apiVersionChecker: ApiVersionChecker by inject()
    private val pluginDirectory: PluginDirectory by inject()
    private val projectRepository: ProjectRepository by inject()
    private val plugin: JavaPlugin by inject()

    /**
     * ダウンロード済みのtempファイルに対してAPIバージョンと依存関係の事前検証を行う
     *
     * force指定時は非互換・依存不足があっても警告ログのみを出力して続行扱いとする
     *
     * @param downloadedFile ダウンロード済みのtempファイル
     * @param pluginName プラグイン名（ログ出力用）
     * @param force trueの場合、非互換・依存不足でも警告のみで続行する
     * @return 検証結果
     */
    suspend fun validate(
        downloadedFile: File,
        pluginName: String,
        force: Boolean
    ): PluginInstallValidationResult {
        // tempファイルからプラグインデータを抽出（破損JARでも例外を握りつぶしてスキップする）
        val pluginData =
            try {
                PluginDataUtils.getPluginData(downloadedFile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                plugin.logger.warning("Failed to read plugin data from downloaded file ($pluginName): ${e.message}")
                null
            }

        // APIバージョンの互換性チェック
        val compatibilityResult = apiVersionChecker.checkCompatibility(downloadedFile)
        when (compatibilityResult) {
            is CompatibilityResult.Incompatible -> {
                if (!force) {
                    return PluginInstallValidationResult.ApiVersionIncompatible(
                        pluginApiVersion = compatibilityResult.pluginApiVersion,
                        serverApiVersion = compatibilityResult.serverApiVersion
                    )
                }
                plugin.logger.warning(
                    "api-version incompatible ($pluginName): " +
                        "plugin=${compatibilityResult.pluginApiVersion}, " +
                        "server=${compatibilityResult.serverApiVersion}. Forced install."
                )
            }
            is CompatibilityResult.Unknown -> {
                plugin.logger.warning(
                    "Cannot verify api-version compatibility ($pluginName): ${compatibilityResult.reason}"
                )
            }
            is CompatibilityResult.Compatible -> {
                // 互換性あり
            }
        }

        // 必須依存関係のチェック
        if (pluginData != null) {
            val requiredDeps =
                when (pluginData) {
                    is PluginData.BukkitPluginData -> pluginData.depend
                    is PluginData.PaperPluginData -> pluginData.depend
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
                                when (val data = PluginDataUtils.getPluginData(jar)) {
                                    is PluginData.BukkitPluginData -> data.name
                                    is PluginData.PaperPluginData -> data.name
                                    null -> null
                                }
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
                    plugin.logger.warning(
                        "$pluginName: 必須依存プラグインが不足しています: " +
                            "${missingDeps.joinToString(", ")} (forced install)"
                    )
                }
            }
        }

        return PluginInstallValidationResult.Valid
    }
}