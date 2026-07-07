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

package party.morino.mpm.api.domain.downloader.model

/**
 * リポジトリ横断検索（`mpm search`）の1件分の結果
 *
 * 各プラットフォームの検索APIから取得した情報を共通形式に正規化したもの。
 *
 * @param source リポジトリ種別（例: "Modrinth", "Hangar", "SpigotMC", "GitHub"）
 * @param slug プラグインの識別子。`mpm add` などで使用できるリポジトリ上のID/スラッグ
 * @param name 表示名
 * @param description 概要説明（取得できない場合はnull）
 * @param downloads ダウンロード数（取得できない場合はnull）
 * @param url プロジェクトページのURL（取得できない場合はnull）
 */
data class PluginSearchResult(
    val source: RepositoryType,
    val slug: String,
    val name: String,
    val description: String? = null,
    val downloads: Long? = null,
    val url: String? = null
)