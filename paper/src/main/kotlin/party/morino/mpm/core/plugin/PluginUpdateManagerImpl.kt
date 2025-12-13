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

package party.morino.mpm.core.plugin

import arrow.core.Either
import party.morino.mpm.api.core.plugin.BulkInstallResult
import party.morino.mpm.api.core.plugin.PluginUpdateManager
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.api.model.plugin.UpdateResult
import party.morino.mpm.core.plugin.usecase.BulkInstallUseCaseImpl
import party.morino.mpm.core.plugin.usecase.LockPluginUseCaseImpl
import party.morino.mpm.core.plugin.usecase.UnlockPluginUseCaseImpl
import party.morino.mpm.core.plugin.usecase.UpdatePluginUseCaseImpl

/**
 * PluginUpdateManagerの実装クラス
 * プラグインの更新、ロック、一括インストールを担当
 */
class PluginUpdateManagerImpl : PluginUpdateManager {
    // UseCaseImplを直接インスタンス化（KoinComponentなので、内部でRepository等をinject()できる）
    private val updatePluginUseCase = UpdatePluginUseCaseImpl()
    private val lockPluginUseCase = LockPluginUseCaseImpl()
    private val unlockPluginUseCase = UnlockPluginUseCaseImpl()
    private val bulkInstallUseCase = BulkInstallUseCaseImpl()

    override suspend fun update(): Either<String, List<UpdateResult>> =
        // UpdatePluginUseCaseに処理を委譲
        updatePluginUseCase.updatePlugins()

    override suspend fun lock(plugin: InstalledPlugin): Either<String, Unit> =
        // LockPluginUseCaseに処理を委譲
        lockPluginUseCase.lockPlugin(plugin.pluginId)

    override suspend fun unlock(plugin: InstalledPlugin): Either<String, Unit> =
        // UnlockPluginUseCaseに処理を委譲
        unlockPluginUseCase.unlockPlugin(plugin.pluginId)

    override suspend fun installAll(): Either<String, BulkInstallResult> =
        // BulkInstallUseCaseに処理を委譲
        bulkInstallUseCase.installAll()
}