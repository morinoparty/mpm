/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.application.model.file

import java.time.Instant

/**
 * plugins/ 直下にあるJARファイル1件の情報
 *
 * mpm の管理情報（mpm.json・メタデータ）とは独立した、ファイルシステム上の事実だけを表す。
 * plugin.yml / paper-plugin.yml を読めた場合はプラグイン名とバージョンも添える。
 *
 * @property fileName ファイル名（plugins/ からの相対）
 * @property sizeBytes ファイルサイズ（バイト）
 * @property lastModified 最終更新日時
 * @property pluginName plugin.yml から読み取ったプラグイン名（読めない・持たないJARは null）
 * @property pluginVersion plugin.yml から読み取ったバージョン（読めない・持たないJARは null）
 * @property running 現在実行中の mpm 自身のJARなら true（即時削除できず、削除は停止時に回される）
 * @property pendingDeletion サーバー停止時の削除が予約済みなら true
 */
data class JarFileInfo(
    val fileName: String,
    val sizeBytes: Long,
    val lastModified: Instant,
    val pluginName: String?,
    val pluginVersion: String?,
    val running: Boolean,
    val pendingDeletion: Boolean
)