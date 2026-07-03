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
import party.morino.mpm.api.domain.config.ConfigManager
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
    // Koinによる依存性注入
    private val plugin: JavaPlugin by inject()
    private val configManager: ConfigManager by inject()

    // ダウンローダーインスタンスを再利用（HttpClientリーク防止）
    // shutdown時に「初期化済みかどうか」を判定するためLazyデリゲートを保持する
    private val spigotDownloaderLazy = lazy { SpigotDownloader() }
    private val modrinthDownloaderLazy = lazy { ModrinthDownloader() }
    private val githubDownloaderLazy = lazy { createGithubDownloader() }
    private val spigotDownloader: SpigotDownloader by spigotDownloaderLazy
    private val modrinthDownloader: ModrinthDownloader by modrinthDownloaderLazy
    private val githubDownloader: GithubDownloader by githubDownloaderLazy

    /**
     * GitHubダウンローダーを生成する
     * 設定にトークンがある場合は認証付きで生成する
     */
    private fun createGithubDownloader(): GithubDownloader {
        val token = configManager.getConfig().settings.githubToken
        return GithubDownloader(token)
    }

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
                // パス segment 数が不足している場合はパース不能としてnullを返す
                if (split.size < 5) return null
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
                // パス segment 数が不足している場合はパース不能としてnullを返す
                if (split.size < 5) return null
                val owner = split[3]
                val projectName = split[4]
                UrlData.HangarUrlData(owner, projectName)
            }

            RepositoryType.MODRINTH -> {
                val split = formattedUrl.split("/")
                // パス segment 数が不足している場合はパース不能としてnullを返す
                if (split.size < 5) return null
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
                githubDownloader.getLatestVersion(urlData)
            }

            is UrlData.SpigotMcUrlData -> {
                spigotDownloader.getLatestVersion(urlData)
            }

            is UrlData.ModrinthUrlData -> {
                modrinthDownloader.getLatestVersion(urlData)
            }

            else -> {
                // 他のリポジトリタイプの実装
                throw Exception("未対応のリポジトリタイプです")
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
                githubDownloader.getVersionByName(urlData, versionName)
            }

            is UrlData.SpigotMcUrlData -> {
                spigotDownloader.getVersionByName(urlData, versionName)
            }

            is UrlData.ModrinthUrlData -> {
                modrinthDownloader.getVersionByName(urlData, versionName)
            }

            else -> {
                // 他のリポジトリタイプの実装
                throw Exception("未対応のリポジトリタイプです")
            }
        }

    /**
     * 指定されたタグ/チャンネルの最新バージョンを取得する
     */
    override suspend fun getLatestVersionByTag(
        urlData: UrlData,
        tag: String
    ): VersionData? =
        when (urlData) {
            is UrlData.ModrinthUrlData -> {
                modrinthDownloader.getLatestVersionByTag(urlData, tag)
            }
            is UrlData.GithubUrlData -> {
                githubDownloader.getLatestVersionByTag(urlData, tag)
            }
            else -> null
        }

    /**
     * 指定されたバージョンのファイルハッシュを取得する
     * Modrinthのみ対応。その他のリポジトリではnullを返す。
     */
    override suspend fun getVersionHashesByName(
        urlData: UrlData,
        versionName: String
    ): Map<String, String>? =
        when (urlData) {
            is UrlData.ModrinthUrlData -> {
                modrinthDownloader.getVersionHashesByName(urlData, versionName)
            }
            else -> null
        }

    /**
     * すべてのバージョンを取得
     * @param urlData URLデータ
     * @return バージョンリスト（新しい順）
     */
    override suspend fun getAllVersions(urlData: UrlData): List<VersionData> =
        when (urlData) {
            is UrlData.GithubUrlData -> {
                githubDownloader.getAllVersions(urlData)
            }

            is UrlData.SpigotMcUrlData -> {
                spigotDownloader.getAllVersions(urlData)
            }

            is UrlData.ModrinthUrlData -> {
                modrinthDownloader.getAllVersions(urlData)
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
                githubDownloader.downloadByVersion(urlData, version, fileNamePattern)
            }

            is UrlData.SpigotMcUrlData -> {
                spigotDownloader.downloadByVersion(urlData, version, fileNamePattern)
            }

            is UrlData.ModrinthUrlData -> {
                modrinthDownloader.downloadByVersion(urlData, version, fileNamePattern)
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
                val latest = githubDownloader.getLatestVersion(urlData as UrlData.GithubUrlData)
                githubDownloader.downloadByVersion(urlData, latest, fileNamePattern)
            }

            RepositoryType.SPIGOTMC -> {
                val latest = spigotDownloader.getLatestVersion(urlData as UrlData.SpigotMcUrlData)
                spigotDownloader.downloadByVersion(urlData, latest, fileNamePattern)
            }

            RepositoryType.HANGER -> {
                // Hangarのダウンロード実装
                null
            }

            RepositoryType.MODRINTH -> {
                val latest = modrinthDownloader.getLatestVersion(urlData as UrlData.ModrinthUrlData)
                modrinthDownloader.downloadByVersion(urlData, latest, fileNamePattern)
            }

            // UNKNOWNタイプはダウンロードできない
            RepositoryType.UNKNOWN -> null
        }
    }

    /**
     * 保持しているダウンローダーのHTTPクライアントを解放する
     * 初期化済みのダウンローダーのみクローズし、未使用のものは生成しない
     * 1つのクローズ失敗が他のクローズを妨げないようにする
     */
    override fun shutdown() {
        listOf(spigotDownloaderLazy, modrinthDownloaderLazy, githubDownloaderLazy)
            .filter { it.isInitialized() }
            .forEach { lazy ->
                runCatching { lazy.value.close() }
            }
    }
}