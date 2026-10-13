/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.velocity.event.listener

import com.velocitypowered.api.event.Subscribe
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.infrastructure.webhook.WebhookEventPublisher
import party.morino.mpm.velocity.event.lifecycle.PluginInstallEvent
import party.morino.mpm.velocity.event.lifecycle.PluginJarDeleteEvent
import party.morino.mpm.velocity.event.lifecycle.PluginRemoveEvent
import party.morino.mpm.velocity.event.lifecycle.PluginUninstallEvent
import party.morino.mpm.velocity.event.state.PluginLockEvent
import party.morino.mpm.velocity.event.state.PluginOutdatedEvent
import party.morino.mpm.velocity.event.state.PluginUnlockEvent
import party.morino.mpm.velocity.event.state.PluginUpdateEvent

/**
 * Velocity 上のプラグイン管理イベントを Discord Webhook に転送するリスナー
 *
 * Velocity は priority が大きいハンドラーから順に呼ぶため、最小の priority で登録して他プラグインの判断の後に通知する。
 * Bukkit の ignoreCancelled に相当する仕組みが無いため、拒否されたイベントは各ハンドラーで読み飛ばす。
 * 通知の文面・配色は Paper と共通の [WebhookEventPublisher] が組み立てる。
 */
class VelocityWebhookEventListener : KoinComponent {
    // KoinによるDI
    private val publisher: WebhookEventPublisher by inject()

    /**
     * プラグインインストールイベントの通知
     */
    @Subscribe(priority = Short.MIN_VALUE)
    fun onPluginInstall(event: PluginInstallEvent) {
        // 他のプラグインに拒否されたインストールは通知しない
        if (!event.result.isAllowed) return
        publisher.pluginInstalled(event.repositoryPlugin.pluginId, event.version, event.repositoryType)
    }

    /**
     * プラグイン更新イベントの通知
     */
    @Subscribe(priority = Short.MIN_VALUE)
    fun onPluginUpdate(event: PluginUpdateEvent) {
        if (!event.result.isAllowed) return
        publisher.pluginUpdated(event.installedPlugin.pluginId, event.beforeVersion, event.targetVersion)
    }

    /**
     * プラグイン管理対象除外イベントの通知
     */
    @Subscribe(priority = Short.MIN_VALUE)
    fun onPluginRemove(event: PluginRemoveEvent) {
        if (!event.result.isAllowed) return
        publisher.pluginRemoved(event.installedPlugin.pluginId)
    }

    /**
     * プラグインアンインストールイベントの通知
     */
    @Subscribe(priority = Short.MIN_VALUE)
    fun onPluginUninstall(event: PluginUninstallEvent) {
        if (!event.result.isAllowed) return
        publisher.pluginUninstalled(event.installedPlugin.pluginId)
    }

    /**
     * プラグインロックイベントの通知
     */
    @Subscribe(priority = Short.MIN_VALUE)
    fun onPluginLock(event: PluginLockEvent) {
        if (!event.result.isAllowed) return
        publisher.pluginLocked(event.installedPlugin.pluginId, event.currentVersion)
    }

    /**
     * プラグインロック解除イベントの通知
     */
    @Subscribe(priority = Short.MIN_VALUE)
    fun onPluginUnlock(event: PluginUnlockEvent) {
        if (!event.result.isAllowed) return
        publisher.pluginUnlocked(event.installedPlugin.pluginId, event.currentVersion)
    }

    /**
     * JARファイル直接削除イベントの通知（拒否できないイベントのため結果は確認しない）
     */
    @Subscribe(priority = Short.MIN_VALUE)
    fun onPluginJarDelete(event: PluginJarDeleteEvent) {
        publisher.pluginJarDeleted(event.fileName, event.deferred)
    }

    /**
     * プラグイン更新可能検出イベントの通知（拒否できないイベントのため結果は確認しない）
     */
    @Subscribe(priority = Short.MIN_VALUE)
    fun onPluginOutdated(event: PluginOutdatedEvent) {
        publisher.pluginOutdated(event.installedPlugin.pluginId, event.currentVersion, event.latestVersion)
    }
}