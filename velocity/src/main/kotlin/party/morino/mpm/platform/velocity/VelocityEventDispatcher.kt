/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.platform.velocity

import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.proxy.ProxyServer
import kotlinx.coroutines.future.await
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.Logger
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import party.morino.mpm.platform.MpmEventDispatcher
import party.morino.mpm.velocity.event.lifecycle.PluginAddEvent
import party.morino.mpm.velocity.event.lifecycle.PluginInstallEvent
import party.morino.mpm.velocity.event.lifecycle.PluginJarDeleteEvent
import party.morino.mpm.velocity.event.lifecycle.PluginRemoveEvent
import party.morino.mpm.velocity.event.lifecycle.PluginUninstallEvent
import party.morino.mpm.velocity.event.state.PluginLockEvent
import party.morino.mpm.velocity.event.state.PluginOutdatedEvent
import party.morino.mpm.velocity.event.state.PluginUnlockEvent
import party.morino.mpm.velocity.event.state.PluginUpdateEvent
import java.io.File
import java.util.concurrent.RejectedExecutionException

/**
 * mpm のイベントを Velocity のイベント（party.morino.mpm.velocity.event.*）として発火する
 *
 * Velocity の EventManager.fire はハンドラーをプラグインのスレッドプールで順に実行し、
 * すべて終わると完了する CompletableFuture を返す。スレッドを塞がないよう await で待つ。
 * ハンドラーの例外は Velocity が握りつぶすため、拒否されたかどうかはイベントの結果から読む。
 */
class VelocityEventDispatcher :
    MpmEventDispatcher,
    KoinComponent {
    // Koinによる依存性注入
    private val proxy: ProxyServer by inject()
    private val logger: Logger by inject()

    override suspend fun firePluginAdd(
        repositoryPlugin: RepositoryPlugin,
        versionSpecifier: VersionSpecifier,
        repositoryType: String,
        repositoryId: String
    ): Boolean = fireResulted(PluginAddEvent(repositoryPlugin, versionSpecifier, repositoryType, repositoryId))

    override suspend fun firePluginInstall(
        repositoryPlugin: RepositoryPlugin,
        version: String,
        repositoryType: String,
        repositoryId: String
    ): Boolean = fireResulted(PluginInstallEvent(repositoryPlugin, version, repositoryType, repositoryId))

    override suspend fun firePluginRemove(installedPlugin: InstalledPlugin): Boolean =
        fireResulted(PluginRemoveEvent(installedPlugin))

    override suspend fun firePluginUninstall(
        installedPlugin: InstalledPlugin,
        jarFile: File?
    ): Boolean = fireResulted(PluginUninstallEvent(installedPlugin, jarFile))

    override suspend fun firePluginUpdate(
        installedPlugin: InstalledPlugin,
        beforeVersion: VersionSpecifier,
        targetVersion: VersionSpecifier
    ): Boolean = fireResulted(PluginUpdateEvent(installedPlugin, beforeVersion, targetVersion))

    override suspend fun firePluginLock(
        installedPlugin: InstalledPlugin,
        currentVersion: String
    ): Boolean = fireResulted(PluginLockEvent(installedPlugin, currentVersion))

    override suspend fun firePluginUnlock(
        installedPlugin: InstalledPlugin,
        currentVersion: String
    ): Boolean = fireResulted(PluginUnlockEvent(installedPlugin, currentVersion))

    override suspend fun firePluginJarDelete(
        fileName: String,
        jarFile: File,
        deferred: Boolean
    ) {
        // 拒否できないイベントのため結果は使わない
        fire(PluginJarDeleteEvent(fileName, jarFile, deferred))
    }

    override suspend fun firePluginOutdated(
        installedPlugin: InstalledPlugin,
        currentVersion: String,
        latestVersion: String
    ) {
        // 拒否できないイベントのため結果は使わない
        fire(PluginOutdatedEvent(installedPlugin, currentVersion, latestVersion))
    }

    /**
     * 結果を持つイベントを発火し、すべてのハンドラーの処理後に許可されているかを返す
     *
     * @param event 発火するイベント
     * @return 許可されている（拒否されなかった）場合 true
     */
    private suspend fun <E : ResultedEvent<*>> fireResulted(event: E): Boolean = fire(event)?.result?.isAllowed ?: true

    /**
     * イベントを発火し、すべてのハンドラーの処理が終わるまで待つ
     *
     * プロキシの停止後はプラグインのスレッドプールが閉じられ、fire が RejectedExecutionException を投げる。
     * その場合は発火できなかったとしてログに残し、処理は止めない（リスナーが居ない場合と同じ扱い）。
     *
     * @param event 発火するイベント
     * @return 処理後のイベント、発火できなかった場合は null
     */
    private suspend fun <E : Any> fire(event: E): E? =
        try {
            proxy.eventManager.fire(event).await()
        } catch (e: RejectedExecutionException) {
            logger.warn("${event::class.simpleName} を発火できませんでした（プロキシの停止処理中）: ${e.message}")
            null
        }
}