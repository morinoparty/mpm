/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
and related and neighboring rights to this software to the public domain worldwide.
This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.model.backup

import kotlinx.serialization.Serializable

/**
 * サーバーバックアップの情報を表すデータクラス
 *
 * @property id バックアップの一意識別子（UUID）
 * @property createdAt バックアップ作成日時（ISO 8601形式）
 * @property reason バックアップを作成した理由
 * @property fileName バックアップファイル名（例: backup-2025-01-18-abc123.zip）
 * @property pluginsIncluded バックアップに含まれるプラグイン名のリスト
 * @property sizeBytes バックアップファイルのサイズ（バイト単位）
 */
@Serializable
data class ServerBackupInfo(
    val id: String,
    val createdAt: String,
    val reason: BackupReason,
    val fileName: String,
    val pluginsIncluded: List<String>,
    val sizeBytes: Long
)