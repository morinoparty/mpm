/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.downloader

import party.morino.mpm.api.domain.downloader.model.RepositoryType
import party.morino.mpm.api.domain.downloader.model.UrlData
import party.morino.mpm.api.domain.downloader.model.VersionData
import java.io.File

/**
 * プラグインダウンローダーのリポジトリインターフェース
 * 各種リポジトリからのプラグインダウンロード操作を定義
 */
interface DownloaderRepository {
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
        fileNamePattern: String?
    ): File?

    /**
     * リポジトリURLからプラグインをダウンロード
     * @param url リポジトリURL
     * @param fileNamePattern ファイル名に一致する正規表現パターン（オプション、複数ファイルがある場合の選択に使用）
     * @return ダウンロードしたファイル
     */
    suspend fun downloadLatest(
        url: String,
        fileNamePattern: String?
    ): File?
}