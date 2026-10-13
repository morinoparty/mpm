/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.event.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.event.lifecycle.PluginInstallEvent
import party.morino.mpm.event.lifecycle.PluginJarDeleteEvent
import party.morino.mpm.event.lifecycle.PluginRemoveEvent
import party.morino.mpm.event.lifecycle.PluginUninstallEvent
import party.morino.mpm.event.state.PluginLockEvent
import party.morino.mpm.event.state.PluginOutdatedEvent
import party.morino.mpm.event.state.PluginUnlockEvent
import party.morino.mpm.event.state.PluginUpdateEvent
import party.morino.mpm.infrastructure.webhook.WebhookEventPublisher

/**
 * プラグイン管理イベントをDiscord Webhookに転送するリスナー
 * EventPriority.MONITORで登録し、キャンセルされなかったイベントのみ通知する
 *
 * 通知の文面・配色は Velocity と共通の [WebhookEventPublisher] が組み立てる。
 */
class WebhookEventListener :
    Listener,
    KoinComponent {
    // KoinによるDI
    private val publisher: WebhookEventPublisher by inject()

    /**
     * プラグインインストールイベントの通知
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPluginInstall(event: PluginInstallEvent) {
        publisher.pluginInstalled(event.repositoryPlugin.pluginId, event.version, event.repositoryType)
    }

    /**
     * プラグイン更新イベントの通知
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPluginUpdate(event: PluginUpdateEvent) {
        publisher.pluginUpdated(event.installedPlugin.pluginId, event.beforeVersion, event.targetVersion)
    }

    /**
     * プラグイン管理対象除外イベントの通知
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPluginRemove(event: PluginRemoveEvent) {
        publisher.pluginRemoved(event.installedPlugin.pluginId)
    }

    /**
     * プラグインアンインストールイベントの通知
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPluginUninstall(event: PluginUninstallEvent) {
        publisher.pluginUninstalled(event.installedPlugin.pluginId)
    }

    /**
     * プラグインロックイベントの通知
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPluginLock(event: PluginLockEvent) {
        publisher.pluginLocked(event.installedPlugin.pluginId, event.currentVersion)
    }

    /**
     * プラグインロック解除イベントの通知
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPluginUnlock(event: PluginUnlockEvent) {
        publisher.pluginUnlocked(event.installedPlugin.pluginId, event.currentVersion)
    }

    /**
     * JARファイル直接削除イベントの通知
     * PluginJarDeleteEventはCancellableではないためignoreCancelledは不要
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPluginJarDelete(event: PluginJarDeleteEvent) {
        publisher.pluginJarDeleted(event.fileName, event.deferred)
    }

    /**
     * プラグイン更新可能検出イベントの通知
     * PluginOutdatedEventはCancellableではないためignoreCancelledは不要
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPluginOutdated(event: PluginOutdatedEvent) {
        publisher.pluginOutdated(event.installedPlugin.pluginId, event.currentVersion, event.latestVersion)
    }
}