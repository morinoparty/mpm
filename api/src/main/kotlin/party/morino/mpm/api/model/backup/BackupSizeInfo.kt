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
 * バックアップ対象のサイズ情報
 */
data class BackupSizeInfo(
    // バックアップ対象の合計バイト数
    val totalSizeBytes: Long,
    // バックアップ対象のファイル総数
    val totalFileCount: Int,
    // トップレベルエントリごとのサイズ内訳
    val entries: List<BackupSizeEntry>,
    // 除外されたエントリ名（selfDir や .mpmignore パターンにマッチしたもの）
    val excludedEntries: List<String>
)