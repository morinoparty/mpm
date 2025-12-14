/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.core.plugin.usecase

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.core.plugin.CheckOutdatedUseCase
import party.morino.mpm.api.core.plugin.PluginInstallUseCase
import party.morino.mpm.api.core.plugin.PluginMetadataManager
import party.morino.mpm.api.core.plugin.UpdatePluginUseCase
import party.morino.mpm.api.model.plugin.UpdateResult

/**
 * mpm updateコマンドに関するユースケースの実装
 * 依存性はKoinによって注入される
 */
class UpdatePluginUseCaseImpl :
    UpdatePluginUseCase,
    KoinComponent {
    // Koinによる依存性注入
    private val checkOutdatedUseCase: CheckOutdatedUseCase by inject()
    private val pluginInstallUseCase: PluginInstallUseCase by inject()
    private val pluginMetadataManager: PluginMetadataManager by inject()

    /**
     * 新しいバージョンがあるすべてのプラグインを更新する
     *
     * @return 成功時は更新結果のリスト、失敗時はエラーメッセージ
     */
    override suspend fun updatePlugins(): Either<String, List<UpdateResult>> {
        // すべてのプラグインの更新情報を取得
        val outdatedInfoList =
            checkOutdatedUseCase.checkAllOutdated().getOrElse {
                return it.left()
            }

        // 更新結果のリスト
        val updateResults = mutableListOf<UpdateResult>()

        // 更新が必要なプラグインを処理
        for (outdatedInfo in outdatedInfoList) {
            // 更新が不要な場合はスキップ
            if (!outdatedInfo.needsUpdate) {
                continue
            }

            // メタデータを読み込んでロック状態を確認
            val metadataEither = pluginMetadataManager.loadMetadata(outdatedInfo.pluginName)
            val metadata =
                if (metadataEither.isLeft()) {
                    // メタデータの読み込みに失敗した場合はエラーを記録
                    updateResults.add(
                        UpdateResult(
                            pluginName = outdatedInfo.pluginName,
                            oldVersion = outdatedInfo.currentVersion,
                            newVersion = outdatedInfo.latestVersion,
                            success = false,
                            errorMessage = "メタデータの読み込みに失敗しました"
                        )
                    )
                    continue
                } else {
                    metadataEither.getOrNull()!!
                }

            // ロックされている場合はスキップ
            if (metadata.mpmInfo.settings.lock == true) {
                updateResults.add(
                    UpdateResult(
                        pluginName = outdatedInfo.pluginName,
                        oldVersion = outdatedInfo.currentVersion,
                        newVersion = outdatedInfo.latestVersion,
                        success = false,
                        errorMessage = "プラグインがロックされています"
                    )
                )
                continue
            }

            // プラグインをインストール（既存のファイルは上書きされる）
            val installResult = pluginInstallUseCase.installPlugin(outdatedInfo.pluginName)

            installResult.fold(
                // インストール失敗時
                { errorMessage ->
                    updateResults.add(
                        UpdateResult(
                            pluginName = outdatedInfo.pluginName,
                            oldVersion = outdatedInfo.currentVersion,
                            newVersion = outdatedInfo.latestVersion,
                            success = false,
                            errorMessage = errorMessage
                        )
                    )
                },
                // インストール成功時
                {
                    updateResults.add(
                        UpdateResult(
                            pluginName = outdatedInfo.pluginName,
                            oldVersion = outdatedInfo.currentVersion,
                            newVersion = outdatedInfo.latestVersion,
                            success = true
                        )
                    )
                }
            )
        }

        return updateResults.right()
    }
}