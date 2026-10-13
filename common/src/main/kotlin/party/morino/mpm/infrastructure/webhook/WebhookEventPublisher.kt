/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.webhook

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.domain.webhook.WebhookEventType
import party.morino.mpm.api.domain.webhook.WebhookNotifier

/**
 * プラグイン管理イベントの内容を Discord Webhook の通知に組み立てて送る
 *
 * Paper（Bukkit イベント）と Velocity（Velocity イベント）のリスナーは、
 * イベントから値を取り出してこのクラスに渡すだけにし、通知の文面・配色を一箇所にまとめる。
 * 該当イベントの通知が無効な場合は何もしない。
 */
class WebhookEventPublisher : KoinComponent {
    // KoinによるDI
    private val webhookNotifier: WebhookNotifier by inject()

    /**
     * プラグインのインストールを通知する
     *
     * @param pluginId プラグインID
     * @param version インストールしたバージョン
     * @param repositoryType リポジトリの種類
     */
    fun pluginInstalled(
        pluginId: String,
        version: String,
        repositoryType: String
    ) = publish(
        eventType = WebhookEventType.INSTALL,
        title = "Plugin Installed",
        description = "プラグイン '$pluginId' がインストールされました",
        color = COLOR_GREEN,
        fields = listOf("Plugin" to pluginId, "Version" to version, "Repository" to repositoryType)
    )

    /**
     * プラグインの更新を通知する
     *
     * @param pluginId プラグインID
     * @param beforeVersion 更新前のバージョン指定
     * @param targetVersion 更新後のバージョン指定
     */
    fun pluginUpdated(
        pluginId: String,
        beforeVersion: VersionSpecifier,
        targetVersion: VersionSpecifier
    ) = publish(
        eventType = WebhookEventType.UPDATE,
        title = "Plugin Updated",
        description = "プラグイン '$pluginId' が更新されました",
        color = COLOR_BLUE,
        fields = listOf("Plugin" to pluginId, "Before" to beforeVersion.toString(), "After" to targetVersion.toString())
    )

    /**
     * プラグインの管理対象からの除外を通知する
     *
     * @param pluginId プラグインID
     */
    fun pluginRemoved(pluginId: String) =
        publish(
            eventType = WebhookEventType.REMOVE,
            title = "Plugin Removed",
            description = "プラグイン '$pluginId' が管理対象から除外されました",
            color = COLOR_RED,
            fields = listOf("Plugin" to pluginId)
        )

    /**
     * プラグインのアンインストールを通知する
     *
     * @param pluginId プラグインID
     */
    fun pluginUninstalled(pluginId: String) =
        publish(
            eventType = WebhookEventType.UNINSTALL,
            title = "Plugin Uninstalled",
            description = "プラグイン '$pluginId' がアンインストールされました",
            color = COLOR_RED,
            fields = listOf("Plugin" to pluginId)
        )

    /**
     * プラグインのロックを通知する
     *
     * @param pluginId プラグインID
     * @param currentVersion ロックしたバージョン
     */
    fun pluginLocked(
        pluginId: String,
        currentVersion: String
    ) = publish(
        eventType = WebhookEventType.LOCK,
        title = "Plugin Locked",
        description = "プラグイン '$pluginId' がロックされました",
        color = COLOR_YELLOW,
        fields = listOf("Plugin" to pluginId, "Version" to currentVersion)
    )

    /**
     * プラグインのロック解除を通知する
     *
     * @param pluginId プラグインID
     * @param currentVersion ロックを解除した時点のバージョン
     */
    fun pluginUnlocked(
        pluginId: String,
        currentVersion: String
    ) = publish(
        eventType = WebhookEventType.UNLOCK,
        title = "Plugin Unlocked",
        description = "プラグイン '$pluginId' のロックが解除されました",
        color = COLOR_YELLOW,
        fields = listOf("Plugin" to pluginId, "Version" to currentVersion)
    )

    /**
     * plugins/ 直下のJARの削除（または削除予約）を通知する
     *
     * @param fileName 削除したファイル名
     * @param deferred サーバー停止時の削除に回した場合 true
     */
    fun pluginJarDeleted(
        fileName: String,
        deferred: Boolean
    ) {
        // 実行中のJARは停止時まで残るため、削除済みか予約済みかを区別して伝える
        val description =
            if (deferred) {
                "JARファイル '$fileName' の削除が予約されました（サーバー停止時に削除されます）"
            } else {
                "JARファイル '$fileName' が削除されました"
            }
        publish(
            eventType = WebhookEventType.FILE_DELETE,
            title = "Plugin File Deleted",
            description = description,
            color = COLOR_RED,
            fields = listOf("File" to fileName, "Deferred" to deferred.toString())
        )
    }

    /**
     * 新しいバージョンの検出を通知する
     *
     * @param pluginId プラグインID
     * @param currentVersion 現在のバージョン
     * @param latestVersion 検出した最新バージョン
     */
    fun pluginOutdated(
        pluginId: String,
        currentVersion: String,
        latestVersion: String
    ) = publish(
        eventType = WebhookEventType.OUTDATED,
        title = "Plugin Outdated",
        description = "プラグイン '$pluginId' に新しいバージョンがあります",
        color = COLOR_ORANGE,
        fields = listOf("Plugin" to pluginId, "Current" to currentVersion, "Latest" to latestVersion)
    )

    /**
     * 通知が有効なイベントのみ Webhook に送る
     */
    private fun publish(
        eventType: WebhookEventType,
        title: String,
        description: String,
        color: Int,
        fields: List<Pair<String, String>>
    ) {
        // 該当イベントの通知が無効なら送らない
        if (!webhookNotifier.isEventEnabled(eventType)) return
        webhookNotifier.notify(eventType, title, description, color, fields)
    }

    // Embed配色定数
    private companion object {
        const val COLOR_GREEN = 0x57F287 // Install
        const val COLOR_BLUE = 0x5865F2 // Update
        const val COLOR_RED = 0xED4245 // Remove / Uninstall
        const val COLOR_YELLOW = 0xFEE75C // Lock / Unlock
        const val COLOR_ORANGE = 0xE67E22 // Outdated
    }
}