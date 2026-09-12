/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.plugin.retire

import kotlinx.serialization.Serializable

/**
 * 削除予約されたJARの一覧（pending-delete.json の内容）
 *
 * @property files plugins ディレクトリ直下のファイル名の一覧
 */
@Serializable
data class PendingJarDeletions(
    val files: List<String> = emptyList()
)