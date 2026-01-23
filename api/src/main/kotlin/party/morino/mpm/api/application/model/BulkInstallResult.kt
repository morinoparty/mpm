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

package party.morino.mpm.api.application.model

/**
 * 一括インストール結果
 *
 * アプリケーション層で使用される結果DTO
 */
data class BulkInstallResult(
    // インストール成功したプラグインの詳細情報
    val installed: List<PluginInstallInfo>,
    // 削除されたファイルの詳細情報
    val removed: List<PluginRemovalInfo>,
    // インストール失敗したプラグイン一覧（プラグイン名 -> エラーメッセージ）
    val failed: Map<String, String>
)