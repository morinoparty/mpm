/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
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
import party.morino.mpm.api.domain.webhook.WebhookEventType
import party.morino.mpm.api.domain.webhook.WebhookNotifier
import party.morino.mpm.event.PluginInstallEvent
import party.morino.mpm.event.PluginLockEvent
import party.morino.mpm.event.PluginOutdatedEvent
import party.morino.mpm.event.PluginRemoveEvent
import party.morino.mpm.event.PluginUninstallEvent
import party.morino.mpm.event.PluginUnlockEvent
import party.morino.mpm.event.PluginUpdateEvent

/**
 * プラグイン管理イベントをDiscord Webhookに転送するリスナー
 * EventPriority.MONITORで登録し、キャンセルされなかったイベントのみ通知する
 */
class WebhookEventListener : Listener, KoinComponent {
    // KoinによるDI
    private val webhookNotifier: WebhookNotifier by inject()

    // Embed配色定数
    companion object {
        private const val COLOR_GREEN = 0x57F287   // Install
        private const val COLOR_BLUE = 0x5865F2    // Update
        private const val COLOR_RED = 0xED4245     // Remove / Uninstall
        private const val COLOR_YELLOW = 0xFEE75C  // Lock / Unlock
        private const val COLOR_ORANGE = 0xE67E22  // Outdated
    }

    /**
     * プラグインインストールイベントの通知
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPluginInstall(event: PluginInstallEvent) {
        if (!webhookNotifier.isEventEnabled(WebhookEventType.INSTALL)) return

        webhookNotifier.notify(
            title = "Plugin Installed",
            description = "プラグイン '${event.repositoryPlugin.pluginId}' がインストールされました",
            color = COLOR_GREEN,
            fields = listOf(
                "Plugin" to event.repositoryPlugin.pluginId,
                "Version" to event.version,
                "Repository" to event.repositoryType
            )
        )
    }

    /**
     * プラグイン更新イベントの通知
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPluginUpdate(event: PluginUpdateEvent) {
        if (!webhookNotifier.isEventEnabled(WebhookEventType.UPDATE)) return

        webhookNotifier.notify(
            title = "Plugin Updated",
            description = "プラグイン '${event.installedPlugin.pluginId}' が更新されました",
            color = COLOR_BLUE,
            fields = listOf(
                "Plugin" to event.installedPlugin.pluginId,
                "Before" to event.beforeVersion.toString(),
                "After" to event.targetVersion.toString()
            )
        )
    }

    /**
     * プラグイン管理対象除外イベントの通知
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPluginRemove(event: PluginRemoveEvent) {
        if (!webhookNotifier.isEventEnabled(WebhookEventType.REMOVE)) return

        webhookNotifier.notify(
            title = "Plugin Removed",
            description = "プラグイン '${event.installedPlugin.pluginId}' が管理対象から除外されました",
            color = COLOR_RED,
            fields = listOf(
                "Plugin" to event.installedPlugin.pluginId
            )
        )
    }

    /**
     * プラグインアンインストールイベントの通知
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPluginUninstall(event: PluginUninstallEvent) {
        if (!webhookNotifier.isEventEnabled(WebhookEventType.UNINSTALL)) return

        webhookNotifier.notify(
            title = "Plugin Uninstalled",
            description = "プラグイン '${event.installedPlugin.pluginId}' がアンインストールされました",
            color = COLOR_RED,
            fields = listOf(
                "Plugin" to event.installedPlugin.pluginId
            )
        )
    }

    /**
     * プラグインロックイベントの通知
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPluginLock(event: PluginLockEvent) {
        if (!webhookNotifier.isEventEnabled(WebhookEventType.LOCK)) return

        webhookNotifier.notify(
            title = "Plugin Locked",
            description = "プラグイン '${event.installedPlugin.pluginId}' がロックされました",
            color = COLOR_YELLOW,
            fields = listOf(
                "Plugin" to event.installedPlugin.pluginId,
                "Version" to event.currentVersion
            )
        )
    }

    /**
     * プラグインロック解除イベントの通知
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPluginUnlock(event: PluginUnlockEvent) {
        if (!webhookNotifier.isEventEnabled(WebhookEventType.UNLOCK)) return

        webhookNotifier.notify(
            title = "Plugin Unlocked",
            description = "プラグイン '${event.installedPlugin.pluginId}' のロックが解除されました",
            color = COLOR_YELLOW,
            fields = listOf(
                "Plugin" to event.installedPlugin.pluginId,
                "Version" to event.currentVersion
            )
        )
    }

    /**
     * プラグイン更新可能検出イベントの通知
     * PluginOutdatedEventはCancellableではないためignoreCancelledは不要
     */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPluginOutdated(event: PluginOutdatedEvent) {
        if (!webhookNotifier.isEventEnabled(WebhookEventType.OUTDATED)) return

        webhookNotifier.notify(
            title = "Plugin Outdated",
            description = "プラグイン '${event.installedPlugin.pluginId}' に新しいバージョンがあります",
            color = COLOR_ORANGE,
            fields = listOf(
                "Plugin" to event.installedPlugin.pluginId,
                "Current" to event.currentVersion,
                "Latest" to event.latestVersion
            )
        )
    }
}
