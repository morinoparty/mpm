/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.model.repository

/**
 * リポジトリURLから抽出されたデータを表すシールドクラス
 */
sealed class UrlData {
    /**
     * GitHubリポジトリのURLデータ
     * @property owner リポジトリの所有者
     * @property repository リポジトリ名
     */
    data class GithubUrlData(
        val owner: String,
        val repository: String
    ) : UrlData()

    /**
     * SpigotMCリポジトリのURLデータ
     * @property resourceId リソースID
     */
    data class SpigotMcUrlData(
        val resourceId: String
    ) : UrlData()

    /**
     * HangarリポジトリのURLデータ
     * @property owner リポジトリの所有者
     * @property projectName プロジェクト名
     */
    data class HangarUrlData(
        val owner: String,
        val projectName: String
    ) : UrlData()

    /**
     * ModrinthリポジトリのURLデータ
     * @property id プロジェクトID
     */
    data class ModrinthUrlData(
        val id: String
    ) : UrlData()
}