/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.downloader.modrinth

import party.morino.mpm.api.domain.downloader.model.RepositoryType
import party.morino.mpm.api.domain.downloader.model.UrlData
import party.morino.mpm.api.domain.downloader.model.VersionData
import party.morino.mpm.infrastructure.downloader.AbstractPluginDownloader
import party.morino.mpm.infrastructure.downloader.modrinth.data.ModrinthProjectInfo
import party.morino.mpm.infrastructure.downloader.modrinth.data.ModrinthVersion
import java.io.File

/**
 * Modrinthからプラグインをダウンロードするクラス
 * テストのためにopenクラスとして定義
 */
open class ModrinthDownloader : AbstractPluginDownloader() {
    /**
     * リポジトリタイプの判定
     * @param url リポジトリURL
     * @return リポジトリタイプ、該当なしの場合はnull
     */
    override fun getRepositoryType(url: String): RepositoryType? {
        // ModrinthのURLパターン: https://modrinth.com/{plugin|mod|datapack|shader}/{id or slug}
        val modrinthPattern =
            Regex("https?://(?:www\\.)?modrinth\\.com/plugin/([^/]+)(?:/.*)?")
        return if (modrinthPattern.matches(url)) RepositoryType.MODRINTH else null
    }

    /**
     * URLデータの抽出
     * @param url リポジトリURL
     * @return 抽出したURLデータ
     */
    override fun getUrlData(url: String): UrlData? {
        // ModrinthのURLからIDまたはslugを抽出
        val modrinthPattern =
            Regex("https?://(?:www\\.)?modrinth\\.com/(plugin|mod|datapack|shader)/([^/]+)(?:/.*)?")
        val match = modrinthPattern.find(url) ?: return null
        val id = match.groupValues[2] // IDまたはslug
        return UrlData.ModrinthUrlData(id)
    }

    /**
     * プロジェクト情報を取得
     * @param urlData ModrinthのURL情報
     * @return プロジェクト情報
     */
    private suspend fun getProjectInfo(urlData: UrlData.ModrinthUrlData): ModrinthProjectInfo {
        val url = "https://api.modrinth.com/v2/project/${urlData.id}"
        val response = getRequest(url, "application/json")
        return json.decodeFromString<ModrinthProjectInfo>(response)
    }

    /**
     * Modrinthのバージョン一覧を取得するヘルパー
     */
    private suspend fun fetchVersions(urlData: UrlData.ModrinthUrlData): List<ModrinthVersion> {
        val loadersParam = java.net.URLEncoder.encode("[\"paper\",\"spigot\"]", "UTF-8")
        val url = "https://api.modrinth.com/v2/project/${urlData.id}/version?loaders=$loadersParam"
        val response = getRequest(url, "application/json")
        return json.decodeFromString<List<ModrinthVersion>>(response)
    }

    /**
     * 最新バージョンを取得
     * チャンネル指定がない場合はreleaseのみを対象とする（beta/alphaを除外）
     * releaseが存在しない場合はフォールバックとして全バージョンの最新を返す
     *
     * @param urlData ModrinthのURL情報
     * @return 最新バージョン名
     */
    override suspend fun getLatestVersion(urlData: UrlData): VersionData {
        urlData as UrlData.ModrinthUrlData
        val versions = fetchVersions(urlData)

        if (versions.isEmpty()) {
            throw Exception("このプロジェクトにはPaper/Spigot対応バージョンがありません")
        }

        // releaseチャンネルのバージョンを優先（beta/alphaを除外）
        val releaseVersions = versions.filter { it.versionType.equals("release", ignoreCase = true) }
        val latestVersion = releaseVersions.firstOrNull() ?: versions[0]
        return VersionData(downloadId = latestVersion.id, version = latestVersion.versionNumber)
    }

    /**
     * 指定されたタグ/チャンネルの最新バージョンを取得する
     *
     * Modrinthのversion_type（release, beta, alpha）でフィルタリングする。
     *
     * @param urlData ModrinthのURL情報
     * @param tag タグ名（"release", "beta", "alpha"）
     * @return 該当タグの最新バージョン、見つからない場合はnull
     */
    override suspend fun getLatestVersionByTag(
        urlData: UrlData,
        tag: String
    ): VersionData? {
        urlData as UrlData.ModrinthUrlData
        val versions = fetchVersions(urlData)

        // version_typeがタグに一致するバージョンをフィルタ
        val filtered = versions.filter { it.versionType.equals(tag, ignoreCase = true) }

        // フィルタ後の最初のバージョン（最新）を返す
        val latest = filtered.firstOrNull() ?: return null
        return VersionData(downloadId = latest.id, version = latest.versionNumber)
    }

    /**
     * 指定されたバージョン名からバージョン情報を取得
     * @param urlData ModrinthのURL情報
     * @param versionName バージョン名
     * @return バージョン情報
     */
    override suspend fun getVersionByName(
        urlData: UrlData,
        versionName: String
    ): VersionData {
        urlData as UrlData.ModrinthUrlData
        val versions = fetchVersions(urlData)

        // 指定されたバージョン名に一致するバージョンを探す
        val matchedVersion =
            versions.firstOrNull { it.versionNumber == versionName }
                ?: throw Exception("バージョン '$versionName' が見つかりませんでした")

        return VersionData(downloadId = matchedVersion.id, version = matchedVersion.versionNumber)
    }

    /**
     * 指定されたバージョンの全ファイルのハッシュを取得する
     *
     * Modrinth APIのレスポンスに含まれるsha1ハッシュを返す。
     * 複数artifactがある場合は全ファイルのsha1をカンマ区切りで返す。
     * バージョンが見つからない場合はnullを返す。
     *
     * @param urlData ModrinthのURL情報
     * @param versionName バージョン名
     * @return ハッシュ情報のMap（例: {"sha1": "hash1,hash2,..."}）、見つからない場合はnull
     */
    override suspend fun getVersionHashesByName(
        urlData: UrlData,
        versionName: String
    ): Map<String, String>? {
        urlData as UrlData.ModrinthUrlData
        val versions = fetchVersions(urlData)

        // 指定バージョンを検索
        val matchedVersion = versions.firstOrNull { it.versionNumber == versionName } ?: return null

        // 全ファイルのsha1ハッシュを収集（複数artifact対応）
        val sha1Hashes = matchedVersion.files
            .mapNotNull { it.hashes?.sha1 }
        if (sha1Hashes.isEmpty()) return null

        return mapOf("sha1" to sha1Hashes.joinToString(","))
    }

    /**
     * すべてのバージョンを取得
     * @param urlData ModrinthのURL情報
     * @return バージョンリスト（新しい順）
     */
    override suspend fun getAllVersions(urlData: UrlData): List<VersionData> {
        urlData as UrlData.ModrinthUrlData
        val versions = fetchVersions(urlData)

        // バージョン情報のリストに変換
        return versions.map { modrinthVersion ->
            VersionData(downloadId = modrinthVersion.id, version = modrinthVersion.versionNumber)
        }
    }

    /**
     * 指定バージョンのプラグインをダウンロード
     * @param urlData ModrinthのURL情報
     * @param version バージョン名
     * @param fileNamePattern ファイル名に一致する正規表現パターン（複数ファイルがある場合の選択に使用）
     * @return ダウンロードしたファイル
     */
    override suspend fun downloadByVersion(
        urlData: UrlData,
        version: VersionData,
        fileNamePattern: String?
    ): File? {
        urlData as UrlData.ModrinthUrlData
        // バージョンIDからバージョン詳細を取得
        val url = "https://api.modrinth.com/v2/version/${version.downloadId}"
        val response = getRequest(url, "application/json")
        val versionInfo = json.decodeFromString<ModrinthVersion>(response)

        if (versionInfo.files.isEmpty()) {
            throw Exception("このバージョンにはファイルがありません")
        }

        // ファイルを選択
        val file =
            if (fileNamePattern == null) {
                // パターンが指定されていない場合はプライマリファイルを優先
                versionInfo.files.firstOrNull { it.primary } ?: versionInfo.files[0]
            } else {
                // パターンにマッチするファイルを検索
                val regex = Regex(fileNamePattern)
                val matchingFile = versionInfo.files.firstOrNull { regex.matches(it.filename) }
                matchingFile
                    ?: throw Exception("パターン '$fileNamePattern' にマッチするファイルが見つかりません")
            }

        // ファイルをダウンロード
        return downloadFile(file.url, file.filename)
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
        val urlData = getUrlData(url) ?: throw Exception("無効なModrinth URL: $url")
        val latestVersion = getLatestVersion(urlData)
        return downloadByVersion(urlData, latestVersion, fileNamePattern)
    }
}