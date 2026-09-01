/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.scheduler

/**
 * プラグインのロック状態
 *
 * メタデータを読めなかった場合を [UNKNOWN] として明示的に分離し、
 * 誤って「ロックされていない＝更新してよい」と扱わないようにする
 */
enum class LockState {
    /** lock = true。更新してはならない */
    LOCKED,

    /** lock されていない。更新してよい */
    UNLOCKED,

    /** メタデータ読み込み失敗などでロック状態を判定できない */
    UNKNOWN
}