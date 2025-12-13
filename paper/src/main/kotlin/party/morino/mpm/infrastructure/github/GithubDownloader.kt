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
 * テストのためにopenクラスとして定義
 */
open class GithubDownloader : AbstractPluginDownloader() {
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
     * 指定されたバージョン名からバージョン情報を取得
     * @param urlData GitHubのURL情報
     * @param versionName バージョン名（tag_name）
     * @return バージョン情報
     */
    override suspend fun getVersionByName(
        urlData: UrlData,
        versionName: String
    ): VersionData {
        urlData as UrlData.GithubUrlData
        // 指定されたタグのリリース情報を取得
        val url = "https://api.github.com/repos/${urlData.owner}/${urlData.repository}/releases/tags/$versionName"
        val response =
            try {
                getRequest(url, "application/vnd.github+json")
            } catch (e: Exception) {
                throw Exception("バージョン '$versionName' が見つかりませんでした: ${e.message}")
            }
        val responseJson = json.parseToJsonElement(response).jsonObject
        // githubのダウンロードではidを利用しない
        val id = responseJson["id"]?.jsonPrimitive?.content ?: "unknown"
        val tagName = responseJson["tag_name"]?.jsonPrimitive?.content ?: "unknown"
        return VersionData(downloadId = id, version = tagName)
    }

    /**
     * すべてのバージョンを取得
     * @param urlData GitHubのURL情報
     * @return バージョンリスト（新しい順）
     */
    override suspend fun getAllVersions(urlData: UrlData): List<VersionData> {
        urlData as UrlData.GithubUrlData
        // すべてのリリースを取得
        val url = "https://api.github.com/repos/${urlData.owner}/${urlData.repository}/releases"
        val response = getRequest(url, "application/vnd.github+json")
        val releases = json.parseToJsonElement(response).jsonArray

        // 各リリースからバージョン情報を抽出
        return releases.map { releaseElement ->
            val releaseJson = releaseElement.jsonObject
            val id = releaseJson["id"]?.jsonPrimitive?.content ?: "unknown"
            val tagName = releaseJson["tag_name"]?.jsonPrimitive?.content ?: "unknown"
            VersionData(downloadId = id, version = tagName)
        }
    }

    /**
     * 指定バージョンのプラグインをダウンロード
     * @param urlData GitHubのURL情報
     * @param version バージョン名
     * @param fileNamePattern ファイル名に一致する正規表現パターン（複数アセットがある場合の選択に使用）
     * @return ダウンロードしたファイル
     */
    override suspend fun downloadByVersion(
        urlData: UrlData,
        version: VersionData,
        fileNamePattern: String?
    ): File? {
        urlData as UrlData.GithubUrlData
        val url =
            "https://api.github.com/repos/${urlData.owner}/${urlData.repository}/releases/" +
                "${version.downloadId}/assets"
        val response = getRequest(url, "application/vnd.github+json")
        // assetsエンドポイントは直接JsonArrayを返す
        val assets = json.parseToJsonElement(response).jsonArray

        if (assets.isEmpty()) {
            throw Exception("このリリースにはアセットがありません")
        }

        // パターンが指定されていない場合は最初のアセットを選択
        val asset =
            if (fileNamePattern == null) {
                assets[0].jsonObject
            } else {
                // パターンにマッチするアセットを検索
                val regex = Regex(fileNamePattern)
                val matchingAsset =
                    assets.firstOrNull { assetElement ->
                        val assetName = assetElement.jsonObject["name"]?.jsonPrimitive?.content ?: ""
                        regex.matches(assetName)
                    }
                matchingAsset?.jsonObject
                    ?: throw Exception("パターン '$fileNamePattern' にマッチするアセットが見つかりません")
            }

        val downloadUrl =
            asset["browser_download_url"]?.jsonPrimitive?.content
                ?: throw Exception("ダウンロードURLが見つかりません")

        val fileName = asset["name"]?.jsonPrimitive?.content ?: "plugin-${LocalDateTime.now()}.jar"
        return downloadFile(downloadUrl, fileName)
    }

    /**
     * リポジトリURLからプラグインをダウンロード
     * @param url リポジトリURL
     * @param fileNamePattern ファイル名に一致する正規表現パターン（オプション、複数ファイルがある場合の選択に使用）
     * @return ダウンロードしたファイル
     */
    override suspend fun downloadLatest(
        url: String,
        fileNamePattern: String?
    ): File? {
        val urlData = getUrlData(url) ?: throw Exception("無効なGitHub URL: $url")
        val latestVersion = getLatestVersion(urlData)
        return downloadByVersion(urlData, latestVersion, fileNamePattern)
    }
}