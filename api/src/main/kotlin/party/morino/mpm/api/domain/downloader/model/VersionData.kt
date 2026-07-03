/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
and related and neighboring rights to this software to the public domain worldwide.
This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.downloader.model

/**
 * ダウンロード対象バージョンの情報
 * @property downloadId ダウンロードURL構築に使うリポジトリ固有の識別子（例: GitHubのrelease id、SpigotMCのバージョンid）
 * @property version 表示・比較用の人間可読なバージョン文字列（例: タグ名、バージョン名）
 */
data class VersionData(
    val downloadId: String,
    val version: String
)