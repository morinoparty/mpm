/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.velocity.event.lifecycle

import java.io.File

/**
 * plugins/ 直下のJARファイルが直接削除されたときに発火するイベント
 *
 * mpm.json やメタデータには触れない、ファイル単体の削除を表す。
 * 削除結果（即時か予約か）が確定してから発火するため、結果を持たない（拒否できない）。
 *
 * @property fileName 削除されたファイル名（plugins/ からの相対）
 * @property jarFile 削除されたJARファイル
 * @property deferred true の場合はまだディスク上に残っており、プロキシ停止時に削除される
 */
class PluginJarDeleteEvent(
    val fileName: String,
    val jarFile: File,
    val deferred: Boolean
)