/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
and related and neighboring rights to this software to the public domain worldwide.
This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.downloader

import party.morino.mpm.api.domain.downloader.model.PluginProjectDetail
import party.morino.mpm.api.domain.downloader.model.PluginSearchResult
import party.morino.mpm.api.domain.downloader.model.RepositoryType
import party.morino.mpm.api.domain.downloader.model.UrlData
import party.morino.mpm.api.domain.downloader.model.VersionData
import java.io.File

/**
 * プラグインダウンローダーのインターフェース
 * 各リポジトリ（SpigotMC、GitHub等）からのプラグインのダウンロードを担当する
 */
interface PluginDownloader {
    /**
     * リポジトリタイプの判定
     * @param url リポジトリURL
     * @return リポジトリタイプ、該当なしの場合はnull
     */
    fun getRepositoryType(url: String): RepositoryType?

    /**
     * URLデータの抽出
     * @param url リポジトリURL
     * @return 抽出したURLデータ
     */
    fun getUrlData(url: String): UrlData?

    /**
     * 最新バージョンの取得
     * @param urlData URLデータ
     * @return 最新バージョン
     */
    suspend fun getLatestVersion(urlData: UrlData): VersionData

    /**
     * 指定されたバージョン名からバージョン情報を取得
     * @param urlData URLデータ
     * @param versionName バージョン名（例: "v5.5.15-bukkit", "1.0.0" など）
     * @return バージョン情報、見つからない場合は例外をスロー
     */
    suspend fun getVersionByName(
        urlData: UrlData,
        versionName: String
    ): VersionData

    /**
     * 指定されたバージョンのファイルハッシュを取得する
     *
     * APIでハッシュ情報を提供するリポジトリ（Modrinth等）のみサポート。
     * 非対応リポジトリではnullを返す。
     *
     * @param urlData URLデータ
     * @param versionName バージョン名
     * @param fileNamePattern ダウンロード時と同じファイル/プラットフォーム選択に使用するパターン（オプション）。
     *   複数artifactを持つリポジトリで、実際にダウンロードするファイルのハッシュのみを対象にするために使用する。
     * @return ハッシュ情報のMap（例: {"sha1": "...", "sha512": "..."}）、非対応の場合はnull
     */
    suspend fun getVersionHashesByName(
        urlData: UrlData,
        versionName: String,
        fileNamePattern: String? = null
    ): Map<String, String>? = null

    /**
     * 指定されたタグ/チャンネルの最新バージョンを取得する
     *
     * Modrinthのversion_type（release/beta/alpha）やGitHubのprereleaseフラグに対応。
     * 非対応リポジトリではnullを返す。
     *
     * @param urlData URLデータ
     * @param tag リリースチャンネル名（"release", "beta", "alpha"）
     * @return 該当チャンネルの最新バージョン、非対応または見つからない場合はnull
     */
    suspend fun getLatestVersionByTag(
        urlData: UrlData,
        tag: String
    ): VersionData? = null

    /**
     * すべてのバージョンを取得
     * @param urlData URLデータ
     * @return バージョンリスト（新しい順）
     */
    suspend fun getAllVersions(urlData: UrlData): List<VersionData>

    /**
     * 指定バージョンのプラグインをダウンロード
     * @param urlData URLデータ
     * @param version バージョン
     * @param fileNamePattern ファイル名に一致する正規表現パターン（オプション、複数ファイルがある場合の選択に使用）
     * @return ダウンロードしたファイル
     */
    suspend fun downloadByVersion(
        urlData: UrlData,
        version: VersionData,
        fileNamePattern: String? = null
    ): File?

    /**
     * リポジトリURLからプラグインをダウンロード
     * @param url リポジトリURL
     * @param fileNamePattern ファイル名に一致する正規表現パターン（オプション、複数ファイルがある場合の選択に使用）
     * @return ダウンロードしたファイル
     */
    suspend fun downloadLatest(
        url: String,
        fileNamePattern: String? = null
    ): File?

    /**
     * キーワードでプラグインを検索する
     *
     * 各プラットフォームの検索APIを呼び出し、結果を共通形式で返す。
     * 検索非対応・失敗の場合は空リストを返す（デフォルト実装は空）。
     *
     * @param query 検索キーワード
     * @param limit 取得件数の上限
     * @return 検索結果（ダウンロード数の多い順が望ましい）
     */
    suspend fun searchPlugins(
        query: String,
        limit: Int
    ): List<PluginSearchResult> = emptyList()

    /**
     * プラグインのプロジェクト詳細を取得する
     *
     * 各プラットフォームのプロジェクト情報APIを呼び出し、共通形式で返す。
     * 取得できない場合はnullを返す（デフォルト実装はnull）。
     *
     * @param urlData URLデータ
     * @return プロジェクト詳細、取得できない場合はnull
     */
    suspend fun getProjectDetail(urlData: UrlData): PluginProjectDetail? = null

    /**
     * 保持しているダウンローダーのリソース（HTTPクライアント等）を解放する
     * プラグイン無効化時に呼び出すことでコネクションリークを防ぐ
     * デフォルト実装は何もしない（後方互換性のため）
     */
    fun shutdown() {}
}