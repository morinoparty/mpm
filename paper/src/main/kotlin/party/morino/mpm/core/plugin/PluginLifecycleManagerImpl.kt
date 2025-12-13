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
import party.morino.mpm.api.core.plugin.InstallResult
import party.morino.mpm.api.core.plugin.PluginLifecycleManager
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.api.model.plugin.Plugin
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import party.morino.mpm.core.plugin.usecase.AddPluginUseCaseImpl
import party.morino.mpm.core.plugin.usecase.PluginInstallUseCaseImpl
import party.morino.mpm.core.plugin.usecase.RemovePluginUseCaseImpl
import party.morino.mpm.core.plugin.usecase.RemoveUnmanagedUseCaseImpl
import party.morino.mpm.core.plugin.usecase.UninstallPluginUseCaseImpl

/**
 * PluginLifecycleManagerの実装クラス
 * プラグインのライフサイクル管理（追加、削除、インストール、アンインストール）を担当
 */
class PluginLifecycleManagerImpl : PluginLifecycleManager {
    // UseCaseImplを直接インスタンス化（KoinComponentなので、内部でRepository等をinject()できる）
    private val addPluginUseCase = AddPluginUseCaseImpl()
    private val removePluginUseCase = RemovePluginUseCaseImpl()
    private val pluginInstallUseCase = PluginInstallUseCaseImpl()
    private val uninstallPluginUseCase = UninstallPluginUseCaseImpl()
    private val removeUnmanagedUseCase = RemoveUnmanagedUseCaseImpl()

    override suspend fun add(
        plugin: RepositoryPlugin,
        version: String
    ): Either<String, Unit> =
        // AddPluginUseCaseに処理を委譲
        addPluginUseCase.addPlugin(plugin.pluginId, version)

    override suspend fun remove(plugin: InstalledPlugin): Either<String, Unit> =
        // RemovePluginUseCaseに処理を委譲
        removePluginUseCase.removePlugin(plugin.pluginId)

    override suspend fun install(plugin: Plugin): Either<String, InstallResult> =
        // PluginInstallUseCaseに処理を委譲
        pluginInstallUseCase.installPlugin(plugin.pluginId)

    override suspend fun uninstall(plugin: InstalledPlugin): Either<String, Unit> =
        // UninstallPluginUseCaseに処理を委譲
        uninstallPluginUseCase.uninstallPlugin(plugin.pluginId)

    override suspend fun removeUnmanaged(): Either<String, List<String>> =
        // RemoveUnmanagedUseCaseに処理を委譲
        removeUnmanagedUseCase.removeUnmanaged()
}