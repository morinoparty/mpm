/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.platform.paper

import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import party.morino.mpm.event.lifecycle.PluginAddEvent
import party.morino.mpm.event.lifecycle.PluginInstallEvent
import party.morino.mpm.event.lifecycle.PluginJarDeleteEvent
import party.morino.mpm.event.lifecycle.PluginRemoveEvent
import party.morino.mpm.event.lifecycle.PluginUninstallEvent
import party.morino.mpm.event.state.PluginLockEvent
import party.morino.mpm.event.state.PluginOutdatedEvent
import party.morino.mpm.event.state.PluginUnlockEvent
import party.morino.mpm.event.state.PluginUpdateEvent
import party.morino.mpm.platform.MpmEventDispatcher
import party.morino.mpm.utils.BukkitDispatcher
import java.io.File

/**
 * mpm のイベントを Bukkit イベント（party.morino.mpm.event.*）として発火する
 *
 * PaperMCではイベントはメインスレッドで発火する必要があるため、[BukkitDispatcher] を使用して
 * メインスレッドへ切り替え、リスナーの処理が終わるまで待つ。
 */
class PaperEventDispatcher :
    MpmEventDispatcher,
    KoinComponent {
    // Koinによる依存性注入
    private val plugin: JavaPlugin by inject()

    override suspend fun firePluginAdd(
        repositoryPlugin: RepositoryPlugin,
        versionSpecifier: VersionSpecifier,
        repositoryType: String,
        repositoryId: String
    ): Boolean =
        !BukkitDispatcher
            .callEventSync(plugin, PluginAddEvent(repositoryPlugin, versionSpecifier, repositoryType, repositoryId))
            .isCancelled

    override suspend fun firePluginInstall(
        repositoryPlugin: RepositoryPlugin,
        version: String,
        repositoryType: String,
        repositoryId: String
    ): Boolean =
        !BukkitDispatcher
            .callEventSync(plugin, PluginInstallEvent(repositoryPlugin, version, repositoryType, repositoryId))
            .isCancelled

    override suspend fun firePluginRemove(installedPlugin: InstalledPlugin): Boolean =
        !BukkitDispatcher.callEventSync(plugin, PluginRemoveEvent(installedPlugin)).isCancelled

    override suspend fun firePluginUninstall(
        installedPlugin: InstalledPlugin,
        jarFile: File?
    ): Boolean = !BukkitDispatcher.callEventSync(plugin, PluginUninstallEvent(installedPlugin, jarFile)).isCancelled

    override suspend fun firePluginUpdate(
        installedPlugin: InstalledPlugin,
        beforeVersion: VersionSpecifier,
        targetVersion: VersionSpecifier
    ): Boolean =
        !BukkitDispatcher
            .callEventSync(plugin, PluginUpdateEvent(installedPlugin, beforeVersion, targetVersion))
            .isCancelled

    override suspend fun firePluginLock(
        installedPlugin: InstalledPlugin,
        currentVersion: String
    ): Boolean = !BukkitDispatcher.callEventSync(plugin, PluginLockEvent(installedPlugin, currentVersion)).isCancelled

    override suspend fun firePluginUnlock(
        installedPlugin: InstalledPlugin,
        currentVersion: String
    ): Boolean = !BukkitDispatcher.callEventSync(plugin, PluginUnlockEvent(installedPlugin, currentVersion)).isCancelled

    override suspend fun firePluginJarDelete(
        fileName: String,
        jarFile: File,
        deferred: Boolean
    ) {
        // キャンセル不可のイベントのため結果は使わない
        BukkitDispatcher.callEventSync(plugin, PluginJarDeleteEvent(fileName, jarFile, deferred))
    }

    override suspend fun firePluginOutdated(
        installedPlugin: InstalledPlugin,
        currentVersion: String,
        latestVersion: String
    ) {
        // キャンセル不可のイベントのため結果は使わない
        BukkitDispatcher.callEventSync(plugin, PluginOutdatedEvent(installedPlugin, currentVersion, latestVersion))
    }
}