/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.webhook

/**
 * Webhook通知を送信するためのインターフェース
 * Discord等の外部サービスへイベント通知を行う
 */
interface WebhookNotifier {
    /**
     * 通知を非同期で送信する（fire-and-forget）
     * 該当イベントが有効なエンドポイントにのみ送信される
     * @param eventType 通知対象のイベント種別
     * @param title Embed のタイトル
     * @param description Embed の説明文
     * @param color Embed の色（10進数）
     * @param fields フィールド一覧（name to value）
     */
    fun notify(
        eventType: WebhookEventType,
        title: String,
        description: String,
        color: Int,
        fields: List<Pair<String, String>> = emptyList()
    )

    /**
     * 指定されたイベント種別の通知が有効かどうかを返す
     * @param eventType イベント種別
     * @return 有効ならtrue
     */
    fun isEventEnabled(eventType: WebhookEventType): Boolean

    /**
     * リソースを解放する（CoroutineScope等のクリーンアップ）
     */
    fun shutdown()
}
