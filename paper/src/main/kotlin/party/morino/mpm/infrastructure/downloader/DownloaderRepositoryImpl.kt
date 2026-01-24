/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.downloader

import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.domain.downloader.DownloaderRepository
import party.morino.mpm.api.domain.downloader.model.RepositoryType
import party.morino.mpm.api.domain.downloader.model.UrlData
import party.morino.mpm.api.domain.downloader.model.VersionData
import party.morino.mpm.infrastructure.downloader.github.GithubDownloader
import party.morino.mpm.infrastructure.downloader.modrinth.ModrinthDownloader
import party.morino.mpm.infrastructure.downloader.spigot.SpigotDownloader
import java.io.File

/**
 * DownloaderRepositoryの実装クラス
 * 各リポジトリタイプに応じたダウンロード処理を提供する
 * 依存性はKoinによって注入される
 */
class DownloaderRepositoryImpl :
    DownloaderRepository,
    KoinComponent {
    // Koinによる依存性注入（現在は未使用だが、将来の拡張のために保持）
    private val plugin: JavaPlugin by inject()

    /**
     * URLからリポジトリタイプを判別
     * @param url 判別するURL
     * @return RepositoryType、該当なしの場合はnull
     */
    override fun getRepositoryType(url: String): RepositoryType? =
        when {
            url.startsWith(RepositoryType.GITHUB.url) -> RepositoryType.GITHUB
            url.startsWith(RepositoryType.SPIGOTMC.url) -> RepositoryType.SPIGOTMC
            url.startsWith(RepositoryType.HANGER.url) -> RepositoryType.HANGER
            url.startsWith(RepositoryType.MODRINTH.url) -> RepositoryType.MODRINTH
            else -> null
        }

    /**
     * URLからURLデータを抽出
     * @param url 抽出対象のURL
     * @return UrlDataオブジェクト、パースできない場合はnull
     */
    override fun getUrlData(url: String): UrlData? {
        val type = getRepositoryType(url) ?: return null
        val formattedUrl = if (url.endsWith("/")) url.dropLast(1) else url

        return when (type) {
            RepositoryType.GITHUB -> {
                val split = formattedUrl.split("/")
                val owner = split[3]
                val repository = split[4]
                UrlData.GithubUrlData(owner, repository)
            }

            RepositoryType.SPIGOTMC -> {
                val resId = formattedUrl.split(".")[(url.split(".").size - 1)]
                UrlData.SpigotMcUrlData(resId)
            }

            RepositoryType.HANGER -> {
                val split = formattedUrl.split("/")
                val owner = split[3]
                val projectName = split[4]
                UrlData.HangarUrlData(owner, projectName)
            }

            RepositoryType.MODRINTH -> {
                val split = formattedUrl.split("/")
                val id = split[4]
                UrlData.ModrinthUrlData(id)
            }

            // UNKNOWNタイプはURLデータを生成できない
            RepositoryType.UNKNOWN -> null
        }
    }

    /**
     * 最新バージョンを取得
     * @param urlData URLデータ
     * @return 最新バージョン文字列
     */
    override suspend fun getLatestVersion(urlData: UrlData): VersionData =
        when (urlData) {
            is UrlData.GithubUrlData -> {
                GithubDownloader().getLatestVersion(urlData)
            }

            is UrlData.SpigotMcUrlData -> {
                SpigotDownloader().getLatestVersion(urlData)
            }

            is UrlData.ModrinthUrlData -> {
                ModrinthDownloader().getLatestVersion(urlData)
            }

            else -> {
                // 他のリポジトリタイプの実装
                VersionData("unknown", "unknown")
            }
        }

    /**
     * 指定されたバージョン名からバージョン情報を取得
     * @param urlData URLデータ
     * @param versionName バージョン名
     * @return バージョン情報
     */
    override suspend fun getVersionByName(
        urlData: UrlData,
        versionName: String
    ): VersionData =
        when (urlData) {
            is UrlData.GithubUrlData -> {
                GithubDownloader().getVersionByName(urlData, versionName)
            }

            is UrlData.SpigotMcUrlData -> {
                SpigotDownloader().getVersionByName(urlData, versionName)
            }

            is UrlData.ModrinthUrlData -> {
                ModrinthDownloader().getVersionByName(urlData, versionName)
            }

            else -> {
                // 他のリポジトリタイプの実装
                throw Exception("未対応のリポジトリタイプです")
            }
        }

    /**
     * すべてのバージョンを取得
     * @param urlData URLデータ
     * @return バージョンリスト（新しい順）
     */
    override suspend fun getAllVersions(urlData: UrlData): List<VersionData> =
        when (urlData) {
            is UrlData.GithubUrlData -> {
                GithubDownloader().getAllVersions(urlData)
            }

            is UrlData.SpigotMcUrlData -> {
                SpigotDownloader().getAllVersions(urlData)
            }

            is UrlData.ModrinthUrlData -> {
                ModrinthDownloader().getAllVersions(urlData)
            }

            else -> {
                // 他のリポジトリタイプの実装
                emptyList()
            }
        }

    /**
     * 指定バージョンのプラグインをダウンロード
     * @param urlData URLデータ
     * @param version バージョン
     * @param fileNamePattern ファイル名に一致する正規表現パターン（オプション、複数ファイルがある場合の選択に使用）
     * @return ダウンロードしたファイル、失敗時はnull
     */
    override suspend fun downloadByVersion(
        urlData: UrlData,
        version: VersionData,
        fileNamePattern: String?
    ): File? =
        when (urlData) {
            is UrlData.GithubUrlData -> {
                GithubDownloader().downloadByVersion(urlData, version, fileNamePattern)
            }

            is UrlData.SpigotMcUrlData -> {
                SpigotDownloader().downloadByVersion(urlData, version, fileNamePattern)
            }

            is UrlData.ModrinthUrlData -> {
                ModrinthDownloader().downloadByVersion(urlData, version, fileNamePattern)
            }

            else -> {
                // 他のリポジトリタイプの実装
                null
            }
        }

    /**
     * URLからプラグインをダウンロード
     * @param url ダウンロード元URL
     * @param fileNamePattern ファイル名に一致する正規表現パターン（オプション、複数ファイルがある場合の選択に使用）
     * @return ダウンロードしたファイル、失敗時はnull
     */
    override suspend fun downloadLatest(
        url: String,
        fileNamePattern: String?
    ): File? {
        val type = getRepositoryType(url) ?: return null
        val urlData = getUrlData(url) ?: return null

        return when (type) {
            RepositoryType.GITHUB -> {
                val downloader = GithubDownloader()
                val latest = downloader.getLatestVersion(urlData as UrlData.GithubUrlData)
                downloader.downloadByVersion(urlData, latest, fileNamePattern)
            }

            RepositoryType.SPIGOTMC -> {
                // SpigotMCのダウンロード実装
                val downloader = SpigotDownloader()
                val latest = downloader.getLatestVersion(urlData as UrlData.SpigotMcUrlData)
                downloader.downloadByVersion(urlData, latest, fileNamePattern)
            }

            RepositoryType.HANGER -> {
                // Hangarのダウンロード実装
                null
            }

            RepositoryType.MODRINTH -> {
                // Modrinthのダウンロード実装
                val downloader = ModrinthDownloader()
                val latest = downloader.getLatestVersion(urlData as UrlData.ModrinthUrlData)
                downloader.downloadByVersion(urlData, latest, fileNamePattern)
            }

            // UNKNOWNタイプはダウンロードできない
            RepositoryType.UNKNOWN -> null
        }
    }
}