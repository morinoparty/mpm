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
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.config.PluginDirectory
import party.morino.mpm.api.config.plugin.MpmConfig
import party.morino.mpm.api.config.plugin.VersionSpecifier
import party.morino.mpm.api.config.plugin.getPluginsSyncingTo
import party.morino.mpm.api.core.plugin.CheckOutdatedUseCase
import party.morino.mpm.api.core.plugin.PluginInstallUseCase
import party.morino.mpm.api.core.plugin.PluginMetadataManager
import party.morino.mpm.api.core.plugin.UpdatePluginUseCase
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.api.model.plugin.UpdateResult
import party.morino.mpm.event.PluginUpdateEvent
import party.morino.mpm.utils.Utils
import java.io.File

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
    private val plugin: JavaPlugin by inject()
    private val pluginDirectory: PluginDirectory by inject()

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

        // mpm.jsonを読み込んでSync依存関係を取得
        val mpmConfig = loadMpmConfig()

        // 更新結果のリスト
        val updateResults = mutableListOf<UpdateResult>()

        // 更新が成功したプラグイン名を追跡（Syncプラグインの連動更新用）
        val updatedPlugins = mutableSetOf<String>()

        // 更新が必要なプラグインを処理
        for (outdatedInfo in outdatedInfoList) {
            // 更新が不要な場合はスキップ
            if (!outdatedInfo.needsUpdate) {
                continue
            }

            // メタデータを読み込んでロック状態を確認
            val metadata = pluginMetadataManager.loadMetadata(outdatedInfo.pluginName).getOrNull()
            if (metadata == null) {
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

            // PluginUpdateEventを発火して、他のプラグインがキャンセルできるようにする
            val updateEvent =
                PluginUpdateEvent(
                    installedPlugin = InstalledPlugin(outdatedInfo.pluginName),
                    beforeVersion = VersionSpecifier.Fixed(outdatedInfo.currentVersion),
                    targetVersion = VersionSpecifier.Fixed(outdatedInfo.latestVersion)
                )
            plugin.server.pluginManager.callEvent(updateEvent)

            // イベントがキャンセルされた場合はスキップ
            if (updateEvent.isCancelled) {
                updateResults.add(
                    UpdateResult(
                        pluginName = outdatedInfo.pluginName,
                        oldVersion = outdatedInfo.currentVersion,
                        newVersion = outdatedInfo.latestVersion,
                        success = false,
                        errorMessage = "更新がキャンセルされました"
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
                    // 更新成功したプラグインを記録
                    updatedPlugins.add(outdatedInfo.pluginName)
                }
            )
        }

        // Syncプラグインの連動更新
        mpmConfig?.let { config ->
            updateSyncPlugins(config, updatedPlugins, updateResults)
        }

        return updateResults.right()
    }

    /**
     * 更新されたプラグインに同期しているプラグインを連動更新する
     *
     * @param mpmConfig 現在の設定
     * @param updatedPlugins 更新されたプラグイン名のセット
     * @param updateResults 更新結果リスト（結果を追加する）
     */
    private suspend fun updateSyncPlugins(
        mpmConfig: MpmConfig,
        updatedPlugins: Set<String>,
        updateResults: MutableList<UpdateResult>
    ) {
        // 更新されたプラグインに同期しているプラグインを取得
        val syncPluginsToUpdate = mutableSetOf<String>()
        for (updatedPlugin in updatedPlugins) {
            val syncingPlugins = mpmConfig.getPluginsSyncingTo(updatedPlugin)
            syncPluginsToUpdate.addAll(syncingPlugins)
        }

        // 既に更新済みのプラグインは除外
        syncPluginsToUpdate.removeAll(updatedPlugins)

        // Syncプラグインを更新
        for (syncPluginName in syncPluginsToUpdate) {
            // メタデータを読み込んで現在のバージョンとロック状態を取得
            val metadataEither = pluginMetadataManager.loadMetadata(syncPluginName)
            val currentVersion =
                metadataEither.fold(
                    { "unknown" },
                    { it.mpmInfo.version.current.raw }
                )

            // ロックされている場合はスキップ
            val isLocked =
                metadataEither.fold(
                    { false },
                    { it.mpmInfo.settings.lock == true }
                )
            if (isLocked) {
                updateResults.add(
                    UpdateResult(
                        pluginName = syncPluginName,
                        oldVersion = currentVersion,
                        newVersion = "sync",
                        success = false,
                        errorMessage = "プラグインがロックされています"
                    )
                )
                continue
            }

            // プラグインをインストール（Syncプラグインのバージョン解決はinstallPlugin内で行われる）
            val installResult = pluginInstallUseCase.installPlugin(syncPluginName)

            installResult.fold(
                // インストール失敗時
                { errorMessage ->
                    updateResults.add(
                        UpdateResult(
                            pluginName = syncPluginName,
                            oldVersion = currentVersion,
                            newVersion = "sync",
                            success = false,
                            errorMessage = "連動更新に失敗: $errorMessage"
                        )
                    )
                },
                // インストール成功時
                {
                    // 新しいバージョンを取得
                    val newVersion =
                        pluginMetadataManager.loadMetadata(syncPluginName).fold(
                            { "unknown" },
                            { it.mpmInfo.version.current.raw }
                        )
                    updateResults.add(
                        UpdateResult(
                            pluginName = syncPluginName,
                            oldVersion = currentVersion,
                            newVersion = newVersion,
                            success = true
                        )
                    )
                }
            )
        }
    }

    /**
     * mpm.jsonを読み込む
     *
     * @return 読み込みに成功した場合はMpmConfig、失敗した場合はnull
     */
    private fun loadMpmConfig(): MpmConfig? {
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "mpm.json")

        if (!configFile.exists()) {
            return null
        }

        return try {
            val jsonString = configFile.readText()
            Utils.json.decodeFromString<MpmConfig>(jsonString)
        } catch (e: Exception) {
            null
        }
    }
}