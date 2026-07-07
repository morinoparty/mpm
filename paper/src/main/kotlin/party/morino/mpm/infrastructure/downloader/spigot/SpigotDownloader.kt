/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.downloader.spigot

import party.morino.mpm.api.domain.downloader.model.PluginProjectDetail
import party.morino.mpm.api.domain.downloader.model.PluginSearchResult
import party.morino.mpm.api.domain.downloader.model.RepositoryType
import party.morino.mpm.api.domain.downloader.model.UrlData
import party.morino.mpm.api.domain.downloader.model.VersionData
import party.morino.mpm.infrastructure.downloader.AbstractPluginDownloader
import party.morino.mpm.infrastructure.downloader.spigot.data.SpigotProjectInfo
import party.morino.mpm.infrastructure.downloader.spigot.data.SpigotResourceDetails
import party.morino.mpm.infrastructure.downloader.spigot.data.SpigotSearchResult
import party.morino.mpm.infrastructure.downloader.spigot.data.SpigotVersionInfo
import java.io.File
import java.net.URLEncoder
import kotlin.coroutines.cancellation.CancellationException

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
        // example https://api.spiget.org/v2/resources/22023/versions?sort=-releaseDate&size=1
        val url = "https://api.spiget.org/v2/resources/${urlData.resourceId}/versions?sort=-releaseDate&size=1"
        val response = getRequest(url, "application/json")
        val versions = json.decodeFromString<List<SpigotVersionInfo>>(response)
        if (versions.isEmpty()) throw Exception("このリソースにはバージョンがありません")
        val latest = versions[0]
        return VersionData(downloadId = latest.id?.toString() ?: "unknown", version = latest.name ?: "unknown")
    }

    /**
     * 指定されたバージョン名からバージョン情報を取得
     * @param urlData SpigotMCのURL情報
     * @param versionName バージョン名
     * @return バージョン情報
     */
    override suspend fun getVersionByName(
        urlData: UrlData,
        versionName: String
    ): VersionData {
        urlData as UrlData.SpigotMcUrlData
        // 全バージョンを取得（size制限なし）
        val url = "https://api.spiget.org/v2/resources/${urlData.resourceId}/versions?sort=-releaseDate"
        val response =
            try {
                getRequest(url, "application/json")
            } catch (e: Exception) {
                throw Exception("バージョン情報の取得に失敗しました: ${e.message}")
            }
        val versions = json.decodeFromString<List<SpigotVersionInfo>>(response)

        // 指定されたバージョン名に一致するバージョンを探す
        val matchedVersion =
            versions.firstOrNull { it.name == versionName }
                ?: throw Exception("バージョン '$versionName' が見つかりませんでした")

        return VersionData(
            downloadId = matchedVersion.id?.toString() ?: "unknown",
            version =
                matchedVersion.name ?: "unknown"
        )
    }

    /**
     * すべてのバージョンを取得
     * @param urlData SpigotMCのURL情報
     * @return バージョンリスト（新しい順）
     */
    override suspend fun getAllVersions(urlData: UrlData): List<VersionData> {
        urlData as UrlData.SpigotMcUrlData
        // 全バージョンを取得（size制限なし、降順ソート）
        val url = "https://api.spiget.org/v2/resources/${urlData.resourceId}/versions?sort=-releaseDate"
        val response = getRequest(url, "application/json")
        val versions = json.decodeFromString<List<SpigotVersionInfo>>(response)

        // バージョン情報のリストに変換
        return versions.map { VersionData(downloadId = it.id?.toString() ?: "unknown", version = it.name ?: "unknown") }
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
        // https://api.spiget.org/v2/resources/62325/versions/latest/download/proxy
        val downloadUrl =
            "https://api.spiget.org/v2/resources/${urlData.resourceId}/versions/" +
                "${version.downloadId}/download/proxy"
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

    /**
     * キーワードでSpigotMCのリソースを検索する
     *
     * Spigetの検索APIを呼び出し、共通形式の検索結果へ変換する。
     * 失敗時は例外を投げず空リストを返す。
     * @param query 検索キーワード
     * @param limit 取得件数の上限
     * @return 検索結果のリスト
     */
    override suspend fun searchPlugins(
        query: String,
        limit: Int
    ): List<PluginSearchResult> =
        try {
            // クエリはパス要素として渡す。URLEncoderは空白を'+'にするが、パスセグメントでは
            // '+'はリテラル扱いになるため、'%20'へ置換して正しくデコードされるようにする
            val encodedQuery = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
            val url =
                "https://api.spiget.org/v2/search/resources/$encodedQuery" +
                    "?size=$limit&fields=id,name,tag,downloads"
            val response = getRequest(url, "application/json")
            // トップレベルはリソースの配列
            val results = json.decodeFromString<List<SpigotSearchResult>>(response)
            results.map { result ->
                PluginSearchResult(
                    source = RepositoryType.SPIGOTMC,
                    slug = result.id.toString(),
                    name = result.name ?: "",
                    description = result.tag,
                    downloads = result.downloads,
                    url = "https://www.spigotmc.org/resources/${result.id}"
                )
            }
        } catch (e: CancellationException) {
            // コルーチンのキャンセルは握り潰さず伝播させる
            throw e
        } catch (e: Exception) {
            // 検索失敗時は空リストを返す
            emptyList()
        }

    /**
     * SpigotMCのプロジェクト詳細を取得する
     *
     * Spigetのリソース情報APIを呼び出し、共通形式へ変換する。
     * latestVersionはサービス層で別途補完するためnullのままにする。
     * 失敗時は例外を投げずnullを返す。
     * @param urlData SpigotMCのURL情報
     * @return プロジェクト詳細、取得できない場合はnull
     */
    override suspend fun getProjectDetail(urlData: UrlData): PluginProjectDetail? =
        try {
            val spigotUrlData = urlData as UrlData.SpigotMcUrlData
            val resourceId = spigotUrlData.resourceId
            val url = "https://api.spiget.org/v2/resources/$resourceId"
            val response = getRequest(url, "application/json")
            val info = json.decodeFromString<SpigotProjectInfo>(response)
            PluginProjectDetail(
                source = RepositoryType.SPIGOTMC,
                slug = resourceId,
                name = info.name ?: "",
                description = info.tag,
                homepage = "https://www.spigotmc.org/resources/$resourceId",
                license = null,
                downloads = info.downloads,
                latestVersion = null
            )
        } catch (e: CancellationException) {
            // コルーチンのキャンセルは握り潰さず伝播させる
            throw e
        } catch (e: Exception) {
            // 取得失敗時はnullを返す
            null
        }
}