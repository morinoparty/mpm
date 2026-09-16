/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.model.backup

import kotlinx.serialization.Serializable

/**
 * バックアップを作成する理由を表す列挙型
 *
 * 自動バックアップ機能は廃止されたため、現在新規に作成されるのは MANUAL のみ。
 * UPDATE / INSTALL はバックアップインデックスに永続化された過去のエントリを
 * 読み込めるように残している（削除するとバックアップ一覧の復元に失敗する）。
 */
@Serializable
enum class BackupReason {
    /** 旧版の mpm update 実行前の自動バックアップ（読み込み互換用） */
    UPDATE,

    /** 旧版の mpm install 実行前の自動バックアップ（読み込み互換用） */
    INSTALL,

    /** mpm backup create による手動バックアップ */
    MANUAL
}