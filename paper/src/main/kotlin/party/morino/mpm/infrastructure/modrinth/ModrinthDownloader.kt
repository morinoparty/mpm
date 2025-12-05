/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.modrinth

import party.morino.mpm.api.model.repository.RepositoryType
import party.morino.mpm.api.model.repository.UrlData
import party.morino.mpm.api.model.repository.VersionData
import party.morino.mpm.core.downloader.AbstractPluginDownloader
import party.morino.mpm.infrastructure.modrinth.data.ModrinthProjectInfo
import party.morino.mpm.infrastructure.modrinth.data.ModrinthVersion
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
     * 最新バージョンを取得
     * @param urlData ModrinthのURL情報
     * @return 最新バージョン名
     */
    override suspend fun getLatestVersion(urlData: UrlData): VersionData {
        urlData as UrlData.ModrinthUrlData
        // Modrinthのバージョン一覧APIを呼び出す
        // loaders=["paper","spigot"]でフィルタリングする
        // URLエンコードが必要な特殊文字を含むため、手動でエンコード
        val loadersParam = java.net.URLEncoder.encode("[\"paper\",\"spigot\"]", "UTF-8")
        val url = "https://api.modrinth.com/v2/project/${urlData.id}/version?loaders=$loadersParam"
        val response = getRequest(url, "application/json")
        val versions = json.decodeFromString<List<ModrinthVersion>>(response)

        if (versions.isEmpty()) {
            throw Exception("このプロジェクトにはPaper/Spigot対応バージョンがありません")
        }

        // 最初のバージョン（最新版）を返す
        val latestVersion = versions[0]
        return VersionData(downloadId = latestVersion.id, version = latestVersion.versionNumber)
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