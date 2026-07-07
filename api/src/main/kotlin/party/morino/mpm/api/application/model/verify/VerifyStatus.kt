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

package party.morino.mpm.api.application.model.verify

/**
 * インストール済みプラグインの整合性再検証（`mpm verify`）の判定結果
 */
enum class VerifyStatus {
    /** 保存済みハッシュと一致 */
    OK,

    /** 保存済みハッシュと不一致（破損・改竄の可能性） */
    MISMATCH,

    /** 保存済みハッシュがなく検証できない */
    NO_HASH,

    /** JARファイルが見つからない */
    FILE_MISSING
}