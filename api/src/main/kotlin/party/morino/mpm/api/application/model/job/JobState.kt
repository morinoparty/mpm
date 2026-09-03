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

/**
 * 非同期ジョブの実行状態
 *
 * クライアントは [RUNNING] の間ポーリングを続け、それ以外になった時点で
 * 結果（[JobSnapshot.result] または [JobSnapshot.errorMessage]）を読む。
 */
enum class JobState {
    /** 実行中 */
    RUNNING,

    /** 正常終了（[JobSnapshot.result] に結果が入る） */
    SUCCEEDED,

    /** 異常終了（[JobSnapshot.errorMessage] に理由が入る） */
    FAILED
}