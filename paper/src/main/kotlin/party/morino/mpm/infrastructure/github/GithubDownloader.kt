/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.github

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import party.morino.mpm.api.model.repository.RepositoryType
import party.morino.mpm.api.model.repository.UrlData
import party.morino.mpm.api.model.repository.VersionData
import party.morino.mpm.core.downloader.AbstractPluginDownloader
import java.io.File
import java.time.LocalDateTime

/**
 * GitHubからプラグインをダウンロードするクラス
 */
class GithubDownloader : AbstractPluginDownloader() {
    /**
     * リポジトリタイプの判定
     * @param url リポジトリURL
     * @return リポジトリタイプ、該当なしの場合はnull
     */
    override fun getRepositoryType(url: String): RepositoryType? {
        val githubPattern = Regex("https?://(?:www\\.)?github\\.com/([^/]+)/([^/]+)(?:/.*)?")
        return if (githubPattern.matches(url)) RepositoryType.GITHUB else null
    }

    /**
     * URLデータの抽出
     * @param url リポジトリURL
     * @return 抽出したURLデータ
     */
    override fun getUrlData(url: String): UrlData? {
        val githubPattern = Regex("https?://(?:www\\.)?github\\.com/([^/]+)/([^/]+)(?:/.*)?")
        val match = githubPattern.find(url) ?: return null
        val owner = match.groupValues[1]
        val repository = match.groupValues[2]
        return UrlData.GithubUrlData(owner, repository)
    }

    /**
     * 最新バージョンを取得
     * @param urlData GitHubのURL情報
     * @return 最新バージョン名
     */
    override suspend fun getLatestVersion(urlData: UrlData): VersionData {
        urlData as UrlData.GithubUrlData
        val url = "https://api.github.com/repos/${urlData.owner}/${urlData.repository}/releases/latest"
        val response = getRequest(url, "application/vnd.github+json")
        val responseJson = json.parseToJsonElement(response).jsonObject
        // githubのダウンロードではidを利用しない
        val id = responseJson["id"]?.jsonPrimitive?.content ?: "unknown"
        val versionName = responseJson["tag_name"]?.jsonPrimitive?.content ?: "unknown"
        return VersionData(downloadId = id, version = versionName)
    }

    /**
     * 指定バージョンのプラグインをダウンロード
     * @param urlData GitHubのURL情報
     * @param version バージョン名
     * @param number アセット番号（複数アセットがある場合）
     * @return ダウンロードしたファイル
     */
    override suspend fun downloadByVersion(
        urlData: UrlData,
        version: VersionData,
        number: Int?
    ): File? {
        urlData as UrlData.GithubUrlData
        val url = "https://api.github.com/repos/${urlData.owner}/${urlData.repository}/releases/${version.downloadId}/assets"
        val response = getRequest(url, "application/vnd.github+json")
        val responseJson = json.parseToJsonElement(response).jsonObject
        val assets = responseJson["assets"]?.jsonArray ?: return null

        if (assets.isEmpty()) {
            throw Exception("このリリースにはアセットがありません")
        }

        //
        val assetIndex = (number ?: 1).coerceIn(1, assets.size) - 1
        val asset = assets[assetIndex].jsonObject

        val downloadUrl =
            asset["browser_download_url"]?.jsonPrimitive?.content
                ?: throw Exception("ダウンロードURLが見つかりません")

        val fileName = asset["name"]?.jsonPrimitive?.content ?: "plugin-${LocalDateTime.now()}.jar"
        return downloadFile(downloadUrl, fileName)
    }

    /**
     * リポジトリURLからプラグインをダウンロード
     * @param url リポジトリURL
     * @param number アセット番号（オプション）
     * @return ダウンロードしたファイル
     */
    override suspend fun downloadLatest(
        url: String,
        number: Int?
    ): File? {
        val urlData = getUrlData(url) ?: throw Exception("無効なGitHub URL: $url")
        val latestVersion = getLatestVersion(urlData)
        return downloadByVersion(urlData, latestVersion, number)
    }
}