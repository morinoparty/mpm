/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.model.backup

/**
 * バックアップサイズ計算における個別エントリ（ファイルまたはディレクトリ）
 */
data class BackupSizeEntry(
    // エントリ名（plugins/直下のファイルまたはフォルダ名）
    val name: String,
    // エントリの合計バイト数
    val sizeBytes: Long,
    // エントリ内のファイル数
    val fileCount: Int
)