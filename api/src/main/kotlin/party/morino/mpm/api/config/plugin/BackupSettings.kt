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

package party.morino.mpm.api.config.plugin

import kotlinx.serialization.Serializable

/**
 * バックアップに関する設定を表すデータクラス
 *
 * @property enabled バックアップ機能が有効かどうか
 * @property maxBackups 保持するバックアップの最大数
 * @property autoBackupOnUpdate update時に自動でバックアップを作成するかどうか
 * @property autoBackupOnInstall install時に自動でバックアップを作成するかどうか
 */
@Serializable
data class BackupSettings(
    val enabled: Boolean = true,
    val maxBackups: Int = 5,
    val autoBackupOnUpdate: Boolean = true,
    val autoBackupOnInstall: Boolean = false
)
