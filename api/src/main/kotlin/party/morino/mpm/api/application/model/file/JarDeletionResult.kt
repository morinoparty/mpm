/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.application.model.file

/**
 * plugins/ 配下のJAR削除結果
 *
 * 実行中のJAR（mpm 自身など）は即時削除するとクラスローダーが壊れるため、
 * その場合は削除せず「サーバー停止時に削除する」予約に切り替える。
 * 呼び出し側は [deferred] を見て、再起動が必要かどうかを利用者に伝える。
 *
 * @property fileName 削除対象のファイル名（plugins/ からの相対）
 * @property deferred true の場合はまだディスク上に残っており、サーバー停止時に削除される
 */
data class JarDeletionResult(
    val fileName: String,
    val deferred: Boolean
)