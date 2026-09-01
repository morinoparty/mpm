/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.config.model

import kotlinx.serialization.Serializable
import party.morino.mpm.api.domain.config.model.webhook.WebhookConfig

/**
 * グローバル設定
 *
 * [autoUpdate] / [autoCheck] / [lock] / [tempDir] は現在どこからも参照されていない予約フィールドである。
 * config.json との互換のために保持しているだけで、値を変えても動作は変わらない
 * （ロックは metadata/<プラグイン名>.yaml の settings.lock、自動更新は [schedule] が決める）。
 */
@Serializable
data class GlobalSettings(
    // デフォルトの自動更新設定（現在は未使用の予約フィールド）
    val autoUpdate: Boolean = false,

    // デフォルトの自動バージョンチェック設定（現在は未使用の予約フィールド）
    val autoCheck: Boolean = false,

    // デフォルトのバージョンロック設定（現在は未使用の予約フィールド）
    val lock: Boolean = false,

    // プラグインの一時保存ディレクトリ（現在は未使用の予約フィールド）
    // 実際のダウンロード用一時ファイルはJVMのシステム一時ディレクトリに作成される
    val tempDir: String = "temp",

    // GitHub APIの認証トークン（レート制限回避のため）
    val githubToken: String? = null,
    // Discord Webhook通知設定
    val webhook: WebhookConfig = WebhookConfig(),
    // スケジュール自動更新設定
    val schedule: ScheduleConfig = ScheduleConfig(),
    // バックアップ設定
    val backup: BackupSettings = BackupSettings(),
    // HTTPメタデータキャッシュ設定
    val cache: CacheSettings = CacheSettings(),
)
