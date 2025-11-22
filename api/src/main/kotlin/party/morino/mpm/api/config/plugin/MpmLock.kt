/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.config.plugin

import kotlinx.serialization.Serializable
import party.morino.mpm.api.model.repository.RepositoryType

/**
 * mpm-lock.yaml ファイルのデータ構造
 *
 * package-lock.jsonに相当する、実際にインストールされたプラグインの正確なバージョンを記録するファイル
 */
@Serializable
data class MpmLock(
    // ロックファイルのバージョン（将来的な互換性のため）
    val lockfileVersion: String = "1.0",

    // 生成日時（ISO 8601形式）
    val generatedAt: String,

    // インストールされたプラグイン
    val plugins: Map<String, LockedPlugin> = emptyMap(),
)

/**
 * ロックされたプラグインの情報
 */
@Serializable
data class LockedPlugin(
    // バージョン情報
    val version: LockedVersion,

    // ダウンロード情報
    val download: DownloadInfo,

    // リポジトリ情報
    val repository: RepositoryInfo,

    // インストール日時（ISO 8601形式）
    val installedAt: String,
)

/**
 * ロックされたバージョン情報
 */
@Serializable
data class LockedVersion(
    // リポジトリから取得した生のバージョン文字列
    // 例: "v5.4.102", "1.0.0", "release-2.0"
    val raw: String,

    // 正規化されたバージョン文字列（比較可能な形式）
    // 例: "5.4.102", "1.0.0", "2.0"
    val normalized: String,
)

/**
 * ダウンロード情報
 */
@Serializable
data class DownloadInfo(
    // ダウンロードURL
    val url: String,

    // ダウンロードに使用したID（リポジトリ固有）
    // - GitHub: release tag or asset ID
    // - Spigot: version ID
    // - Modrinth: version ID
    // - Hangar: version ID
    val downloadId: String,

    // ダウンロードされたファイル名
    val fileName: String,

    // ファイルのSHA-256ハッシュ値（整合性チェック用）
    val sha256: String,
)

