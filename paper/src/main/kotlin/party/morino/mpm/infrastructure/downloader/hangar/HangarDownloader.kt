/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.downloader.hangar

import party.morino.mpm.api.domain.downloader.model.RepositoryType
import party.morino.mpm.api.domain.downloader.model.UrlData
import party.morino.mpm.api.domain.downloader.model.VersionData
import party.morino.mpm.infrastructure.downloader.AbstractPluginDownloader
import party.morino.mpm.infrastructure.downloader.hangar.data.HangarVersion
import party.morino.mpm.infrastructure.downloader.hangar.data.HangarVersionsResponse
import java.io.File

/**
 * Hangar（PaperMC公式プラグインリポジトリ）からプラグインをダウンロードするクラス
 *
 * Hangar API v1（https://hangar.papermc.io/api-docs）を利用する。
 * Hangarではバージョン名がそのままバージョン文字列となるため、VersionDataの
 * downloadIdとversionには同じ値（バージョン名）を格納する。
 *
 * テストのためにopenクラスとして定義
 */
open class HangarDownloader : AbstractPluginDownloader() {
    // HangarのURLパターン: https://hangar.papermc.io/{owner}/{project}
    private val hangarPattern =
        Regex("https?://hangar\\.papermc\\.io/([^/]+)/([^/]+)(?:/.*)?")

    // Paper向けプラグイン管理のため、ダウンロード時に優先するプラットフォーム
    private val preferredPlatform = "PAPER"

    // 1ページあたりの取得件数（Hangar APIの上限は25）
    private val pageSize = 25L

    /**
     * リポジトリタイプの判定
     * @param url リポジトリURL
     * @return リポジトリタイプ、該当なしの場合はnull
     */
    override fun getRepositoryType(url: String): RepositoryType? =
        if (hangarPattern.matches(url)) RepositoryType.HANGAR else null

    /**
     * URLデータの抽出
     * @param url リポジトリURL
     * @return 抽出したURLデータ
     */
    override fun getUrlData(url: String): UrlData? {
        val match = hangarPattern.find(url) ?: return null
        val owner = match.groupValues[1]
        val projectName = match.groupValues[2]
        return UrlData.HangarUrlData(owner, projectName)
    }

    /**
     * すべてのバージョンをページネーションで巡回して取得するヘルパー
     *
     * Hangar APIはページングされるため、全件取得するには全ページを巡回する必要がある。
     * @param urlData HangarのURL情報
     * @return バージョンのリスト（新しい順）
     */
    private suspend fun fetchAllVersions(urlData: UrlData.HangarUrlData): List<HangarVersion> {
        val versions = mutableListOf<HangarVersion>()
        var offset = 0L
        while (true) {
            val url =
                "https://hangar.papermc.io/api/v1/projects/${urlData.projectName}/versions" +
                    "?limit=$pageSize&offset=$offset"
            val response = getRequest(url, "application/json")
            val page = json.decodeFromString<HangarVersionsResponse>(response)
            versions.addAll(page.result)
            // 実際に取得できた件数だけoffsetを進める
            // （サーバーがpageSize未満に切り詰めても中間バージョンを飛ばさないため）
            offset += page.result.size
            // 取得済み件数が全件数に達したか、空ページに到達したら終了
            if (offset >= page.pagination.count || page.result.isEmpty()) break
        }
        return versions
    }

    /**
     * 最新バージョンの取得
     *
     * チャンネル指定がない場合はReleaseチャンネルを優先する（Beta/Alpha/Snapshotを除外）。
     * Releaseが存在しない場合はフォールバックとして全バージョンの最新を返す。
     *
     * @param urlData HangarのURL情報
     * @return 最新バージョン
     */
    override suspend fun getLatestVersion(urlData: UrlData): VersionData {
        urlData as UrlData.HangarUrlData
        val versions = fetchAllVersions(urlData)
        if (versions.isEmpty()) throw Exception("このプロジェクトにはバージョンがありません")

        // Releaseチャンネルを優先し、無ければ全体の最新にフォールバック
        val releaseVersions = versions.filter { it.channel.name.equals("Release", ignoreCase = true) }
        val latest = releaseVersions.firstOrNull() ?: versions[0]
        return VersionData(downloadId = latest.name, version = latest.name)
    }

    /**
     * 指定されたタグ/チャンネルの最新バージョンを取得する
     *
     * Hangarのチャンネル名（Release, Snapshot, Beta, Alphaなど）でフィルタリングする。
     * 大文字小文字は区別しない。
     *
     * @param urlData HangarのURL情報
     * @param tag タグ名（"release", "beta", "alpha", "snapshot"など）
     * @return 該当タグの最新バージョン、見つからない場合はnull
     */
    override suspend fun getLatestVersionByTag(
        urlData: UrlData,
        tag: String
    ): VersionData? {
        urlData as UrlData.HangarUrlData
        val versions = fetchAllVersions(urlData)

        // チャンネル名がタグに一致するバージョンをフィルタ（最初の要素が最新）
        val latest =
            versions.firstOrNull { it.channel.name.equals(tag, ignoreCase = true) } ?: return null
        return VersionData(downloadId = latest.name, version = latest.name)
    }

    /**
     * 指定されたバージョン名からバージョン情報を取得
     * @param urlData HangarのURL情報
     * @param versionName バージョン名
     * @return バージョン情報
     */
    override suspend fun getVersionByName(
        urlData: UrlData,
        versionName: String
    ): VersionData {
        urlData as UrlData.HangarUrlData
        val versions = fetchAllVersions(urlData)

        // 指定されたバージョン名に一致するバージョンを探す
        val matched =
            versions.firstOrNull { it.name == versionName }
                ?: throw Exception("バージョン '$versionName' が見つかりませんでした")
        return VersionData(downloadId = matched.name, version = matched.name)
    }

    /**
     * すべてのバージョンを取得
     * @param urlData HangarのURL情報
     * @return バージョンリスト（新しい順）
     */
    override suspend fun getAllVersions(urlData: UrlData): List<VersionData> {
        urlData as UrlData.HangarUrlData
        return fetchAllVersions(urlData).map { VersionData(downloadId = it.name, version = it.name) }
    }

    /**
     * 指定バージョンのプラグインをダウンロード
     *
     * バージョン詳細を取得し、プラットフォーム別のダウンロードURLからファイルを取得する。
     * fileNamePatternが指定された場合はファイル名に一致するプラットフォームを選択し、
     * 未指定の場合はPAPERを優先する。
     *
     * @param urlData HangarのURL情報
     * @param version バージョン（downloadIdにバージョン名が入る）
     * @param fileNamePattern ファイル名に一致する正規表現パターン（複数プラットフォームがある場合の選択に使用）
     * @return ダウンロードしたファイル
     */
    override suspend fun downloadByVersion(
        urlData: UrlData,
        version: VersionData,
        fileNamePattern: String?
    ): File? {
        urlData as UrlData.HangarUrlData
        // バージョン名からバージョン詳細を取得
        val url = "https://hangar.papermc.io/api/v1/projects/${urlData.projectName}/versions/${version.downloadId}"
        val response = getRequest(url, "application/json")
        val versionInfo = json.decodeFromString<HangarVersion>(response)

        // ダウンロード可能なプラットフォームを選択（url, fileName）
        val (downloadUrl, fileName) = selectDownload(versionInfo, fileNamePattern)
        return downloadFile(downloadUrl, fileName)
    }

    /**
     * ダウンロード対象のプラットフォームを選択し、URLとファイル名を返す
     *
     * @param version バージョン情報
     * @param fileNamePattern ファイル名に一致する正規表現パターン（オプション）
     * @return ダウンロードURLとファイル名のペア
     */
    private fun selectDownload(
        version: HangarVersion,
        fileNamePattern: String?
    ): Pair<String, String> {
        // ダウンロードURL（Hangarホスト優先、無ければ外部URL）を解決するヘルパー
        fun resolveUrl(platform: String) = version.downloads[platform]?.let { it.downloadUrl ?: it.externalUrl }

        val chosenPlatform =
            if (fileNamePattern != null) {
                // パターンにマッチするファイル名を持つプラットフォームを選択
                val regex = Regex(fileNamePattern)
                version.downloads.entries
                    .firstOrNull { (platform, download) ->
                        download.fileInfo?.name?.let { regex.matches(it) } == true && resolveUrl(platform) != null
                    }?.key
                    ?: throw Exception("パターン '$fileNamePattern' にマッチするファイルが見つかりません")
            } else {
                // PAPERを優先し、無ければダウンロード可能な最初のプラットフォーム
                version.downloads.keys.firstOrNull { it == preferredPlatform && resolveUrl(it) != null }
                    ?: version.downloads.keys.firstOrNull { resolveUrl(it) != null }
                    ?: throw Exception("このバージョンにはダウンロード可能なファイルがありません")
            }

        val download = version.downloads.getValue(chosenPlatform)
        val downloadUrl =
            download.downloadUrl ?: download.externalUrl
                ?: throw Exception("ダウンロードURLが見つかりません")
        // ファイル名はfileInfoから、無ければバージョン名から生成
        val fileName = download.fileInfo?.name ?: "${version.name}.jar"
        return downloadUrl to fileName
    }

    /**
     * リポジトリURLからプラグインをダウンロード
     * @param url リポジトリURL
     * @param fileNamePattern ファイル名に一致する正規表現パターン（オプション）
     * @return ダウンロードしたファイル
     */
    override suspend fun downloadLatest(
        url: String,
        fileNamePattern: String?
    ): File? {
        val urlData = getUrlData(url) ?: throw Exception("無効なHangar URL: $url")
        val latestVersion = getLatestVersion(urlData)
        return downloadByVersion(urlData, latestVersion, fileNamePattern)
    }
}