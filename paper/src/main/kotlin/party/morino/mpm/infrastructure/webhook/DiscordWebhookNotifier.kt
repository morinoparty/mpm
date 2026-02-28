/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.webhook

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.domain.config.ConfigManager
import party.morino.mpm.api.domain.webhook.WebhookEventType
import party.morino.mpm.api.domain.webhook.WebhookNotifier
import party.morino.mpm.infrastructure.webhook.model.DiscordEmbed
import party.morino.mpm.infrastructure.webhook.model.DiscordEmbedField
import party.morino.mpm.infrastructure.webhook.model.DiscordWebhookPayload
import java.net.URI
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Discord Webhook APIを使用した通知送信の実装
 * fire-and-forget方式で非同期に通知を送信する
 */
class DiscordWebhookNotifier : WebhookNotifier, KoinComponent {
    // KoinによるDI
    private val configManager: ConfigManager by inject()
    private val plugin: JavaPlugin by inject()

    // 非同期送信用のCoroutineScope（SupervisorJobで個別失敗を隔離）
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // HTTP クライアント（Discord Webhook専用）
    private val httpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 10000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 10000
        }
    }

    // JSONシリアライザ（Discord APIにはprettyPrint不要）
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    companion object {
        // Discord Webhook URLの許可ドメイン
        private val ALLOWED_HOSTS = setOf("discord.com", "discordapp.com")

        // Discord Embed仕様の文字数制限
        private const val MAX_TITLE_LENGTH = 256
        private const val MAX_DESCRIPTION_LENGTH = 4096
        private const val MAX_FIELD_NAME_LENGTH = 256
        private const val MAX_FIELD_VALUE_LENGTH = 1024
    }

    /**
     * Discord Webhookへ通知を非同期で送信する
     * 送信失敗時はログに記録するのみで、呼び出し元には影響しない
     */
    override fun notify(
        title: String,
        description: String,
        color: Int,
        fields: List<Pair<String, String>>
    ) {
        val config = configManager.getConfig().settings.webhook

        // Webhookが無効またはURLが空の場合はスキップ
        if (!config.enabled || config.url.isBlank()) return

        // URLのバリデーション（SSRF防止: httpsかつDiscordドメインのみ許可）
        if (!isValidWebhookUrl(config.url)) {
            plugin.logger.warning("Webhook URLが無効です。httpsでDiscordドメインのURLを指定してください: ${config.url}")
            return
        }

        // Embedを構築（Discord仕様の文字数制限に従い切り詰め）
        val embed = DiscordEmbed(
            title = title.take(MAX_TITLE_LENGTH),
            description = description.take(MAX_DESCRIPTION_LENGTH),
            color = color,
            fields = fields.map { (name, value) ->
                DiscordEmbedField(
                    name.take(MAX_FIELD_NAME_LENGTH),
                    value.take(MAX_FIELD_VALUE_LENGTH)
                )
            },
            timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now()),
            footer = DiscordEmbed.Footer(text = "MinecraftPluginManager")
        )

        val payload = DiscordWebhookPayload(embeds = listOf(embed))

        // fire-and-forget: 非同期でPOSTリクエストを送信
        scope.launch {
            try {
                val response = httpClient.post(config.url) {
                    contentType(ContentType.Application.Json)
                    setBody(json.encodeToString(payload))
                }

                // Discord Webhookは204 No Contentを返す
                if (!response.status.isSuccess()) {
                    // 429 Too Many Requests: レートリミット超過
                    if (response.status.value == 429) {
                        plugin.logger.warning("Webhook送信がレートリミットに達しました。しばらく待ってから再試行してください。")
                    } else {
                        plugin.logger.warning("Webhook送信に失敗しました: HTTP ${response.status}")
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("Webhook送信中にエラーが発生しました: ${e.message}")
            }
        }
    }

    /**
     * Webhook URLがDiscordの正当なURLかどうかを検証する
     * SSRF防止のため、httpsスキームかつDiscordドメインのみ許可
     * @param url 検証対象のURL
     * @return 有効なDiscord Webhook URLならtrue
     */
    private fun isValidWebhookUrl(url: String): Boolean {
        return try {
            val uri = URI(url)
            // httpsスキームのみ許可
            uri.scheme == "https" &&
                // Discordドメインのみ許可
                ALLOWED_HOSTS.contains(uri.host)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 指定されたイベント種別の通知が有効かどうかを返す
     */
    override fun isEventEnabled(eventType: WebhookEventType): Boolean {
        val config = configManager.getConfig().settings.webhook

        // Webhook自体が無効なら全イベント無効
        if (!config.enabled || config.url.isBlank()) return false

        // イベントごとの設定を確認
        return when (eventType) {
            WebhookEventType.INSTALL -> config.events.install
            WebhookEventType.UPDATE -> config.events.update
            WebhookEventType.REMOVE -> config.events.remove
            WebhookEventType.UNINSTALL -> config.events.uninstall
            WebhookEventType.LOCK -> config.events.lock
            WebhookEventType.UNLOCK -> config.events.unlock
            WebhookEventType.OUTDATED -> config.events.outdated
        }
    }

    /**
     * リソースを解放する
     */
    override fun shutdown() {
        scope.cancel()
        httpClient.close()
    }
}
