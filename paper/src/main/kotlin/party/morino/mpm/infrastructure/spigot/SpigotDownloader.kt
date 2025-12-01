/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.spigot

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import party.morino.mpm.api.model.repository.RepositoryType
import party.morino.mpm.api.model.repository.UrlData
import party.morino.mpm.api.model.repository.VersionData
import party.morino.mpm.core.downloader.AbstractPluginDownloader
import party.morino.mpm.infrastructure.spigot.data.SpigotResourceDetails
import java.io.File

/**
 * SpigotMCからプラグインをダウンロードするクラス
 * テストのためにopenクラスとして定義
 */
open class SpigotDownloader : AbstractPluginDownloader() {
    /**
     * リポジトリタイプの判定
     * @param url リポジトリURL
     * @return リポジトリタイプ、該当なしの場合はnull
     */
    override fun getRepositoryType(url: String): RepositoryType? {
        val spigotPattern = Regex("https?://(?:www\\.)?spigotmc\\.org/resources/(?:.+\\.)?([0-9]+)(?:/.*)?")
        return if (spigotPattern.matches(url)) RepositoryType.SPIGOTMC else null
    }

    /**
     * URLデータの抽出
     * @param url リポジトリURL
     * @return 抽出したURLデータ
     */
    override fun getUrlData(url: String): UrlData? {
        val spigotPattern = Regex("https?://(?:www\\.)?spigotmc\\.org/resources/(?:.+\\.)?([0-9]+)(?:/.*)?")
        val match = spigotPattern.find(url) ?: return null
        val resourceId = match.groupValues[1]
        return UrlData.SpigotMcUrlData(resourceId)
    }

    /**
     * リソース詳細情報を取得
     * @param urlData SpigotMCのURL情報
     * @return リソース詳細情報
     */
    private suspend fun getDetails(urlData: UrlData.SpigotMcUrlData): SpigotResourceDetails {
        val url = "https://api.spiget.org/v2/resources/${urlData.resourceId}"
        val response = getRequest(url, "application/json")
        return json.decodeFromString<SpigotResourceDetails>(response)
    }

    /**
     * 最新バージョンの取得
     * @param urlData URLデータ
     * @return 最新バージョン
     */
    override suspend fun getLatestVersion(urlData: UrlData): VersionData {
        urlData as UrlData.SpigotMcUrlData
        // example https://api.spiget.org/v2/resources/22023/versions?sort=-name&size=1
        val url = "https://api.spiget.org/v2/resources/${urlData.resourceId}/versions?sort=-name&size=1"
        val response = getRequest(url, "application/json")
        val responseJson = json.parseToJsonElement(response).jsonArray
        val version = responseJson[0].jsonObject["name"]?.jsonPrimitive?.content ?: "unknown"
        val id = responseJson[0].jsonObject["id"]?.jsonPrimitive?.content ?: "unknown"
        return VersionData(downloadId = id, version = version)
    }

    /**
     * 指定バージョンのプラグインをダウンロード
     * @param urlData URLデータ
     * @param version バージョン
     * @param fileNamePattern ファイル名に一致する正規表現パターン（SpigotMCでは使用しない）
     * @return ダウンロードしたファイル
     */
    override suspend fun downloadByVersion(
        urlData: UrlData,
        version: VersionData,
        fileNamePattern: String?
    ): File? {
        urlData as UrlData.SpigotMcUrlData
        val details = getDetails(urlData)
        val downloadUrl =
            "https://api.spiget.org/v2/resources/${urlData.resourceId}/versions/" +
                "${version.downloadId}/download"
        val fileName = "${details.name}-${version.version}.jar"
        return downloadFile(downloadUrl, fileName)
    }

    /**
     * リポジトリURLからプラグインをダウンロード
     * @param url リポジトリURL
     * @param fileNamePattern ファイル名に一致する正規表現パターン（SpigotMCでは使用しない）
     * @return ダウンロードしたファイル
     */
    override suspend fun downloadLatest(
        url: String,
        fileNamePattern: String?
    ): File? {
        val urlData = getUrlData(url) ?: throw Exception("無効なSpigotMC URL: $url")
        val latestVersion = getLatestVersion(urlData)
        return downloadByVersion(urlData, latestVersion, fileNamePattern)
    }
}