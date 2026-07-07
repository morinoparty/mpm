/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.downloader.github.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * GitHubのリポジトリ検索結果の1件分
 *
 * @property fullName リポジトリのフルネーム（owner/repository形式）
 * @property description リポジトリの概要説明（未設定の場合はnull）
 * @property htmlUrl リポジトリのWebページURL
 * @property stargazersCount スター数（GitHubにはダウンロード数がないため代替指標）
 */
@Serializable
data class GithubSearchItem(
    @SerialName("full_name")
    val fullName: String,
    val description: String? = null,
    @SerialName("html_url")
    val htmlUrl: String? = null,
    @SerialName("stargazers_count")
    val stargazersCount: Long? = null
)