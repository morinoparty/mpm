/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
 * and related and neighboring rights to this software to the public domain worldwide.
 * This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.application.model.job

import java.time.Instant

/**
 * 進捗ログの1行
 *
 * サービス層の進捗コールバックはゲーム内チャット向けのMiniMessage形式で
 * メッセージを流すため、Webクライアントがそのまま表示できるように
 * タグを除去した平文（[text]）も併せて保持する。
 *
 * @property timestamp 記録時刻
 * @property raw サービス層が発行したMiniMessage形式の原文
 * @property text MiniMessageタグを除去した平文
 */
data class JobProgressEntry(
    val timestamp: Instant,
    val raw: String,
    val text: String
)