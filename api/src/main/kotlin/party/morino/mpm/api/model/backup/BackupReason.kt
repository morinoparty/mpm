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
 * バックアップを作成する理由を表す列挙型
 */
@Serializable
enum class BackupReason {
    /** mpm update 実行前の自動バックアップ */
    UPDATE,

    /** mpm install 実行前の自動バックアップ */
    INSTALL,

    /** mpm backup create による手動バックアップ */
    MANUAL
}