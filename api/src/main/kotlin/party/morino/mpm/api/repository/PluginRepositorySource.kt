/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.repository

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * プラグインリポジトリソースのインターフェース
 * ローカルまたはリモートからリポジトリファイルを取得する
 */
interface PluginRepositorySource {
    /**
     * このソースが利用可能かどうかを確認
     * @return 利用可能な場合はtrue
     */
    suspend fun isAvailable(): Boolean

    /**
     * 利用可能なプラグインの一覧を取得
     * @return プラグイン名のリスト
     */
    suspend fun getAvailablePlugins(): List<String>

    /**
     * 指定したプラグインのリポジトリファイルを取得
     * @param pluginName プラグイン名
     * @return リポジトリファイルの内容、見つからない場合はnull
     */
    suspend fun getRepositoryFile(pluginName: String): RepositoryFile?

    /**
     * リポジトリソースの種類を取得
     * @return ソースの種類（"local", "remote"など）
     */
    fun getSourceType(): String

    /**
     * リポジトリソースの識別子を取得
     * @return 識別子（パスやURLなど）
     */
    fun getIdentifier(): String
}

/**
 * リポジトリファイルのデータ構造
 * @property id プラグインID
 * @property website プラグインのウェブサイトURL
 * @property source ソースコードのURL
 * @property license ライセンス（例: GPL-3.0, MIT）
 * @property repositories ダウンロード元のリポジトリ設定リスト
 */
@Serializable
data class RepositoryFile(
    val id: String,
    val website: String? = null,
    val source: String? = null,
    val license: String? = null,
    val repositories: List<RepositoryConfig>
)

/**
 * リポジトリ設定
 * プラグインのダウンロード元とダウンロード方法を定義する
 *
 * @property type リポジトリタイプ（modrinth, github, spigotmc, hangarなど）
 * @property repositoryId リポジトリ固有のID
 *   - GitHub: "owner/repository"
 *   - SpigotMC: リソースID（例: "12345"）
 *   - Modrinth: プロジェクトIDまたはslug（例: "luckperms"）
 * @property versionPattern バージョン番号の検証用正規表現（オプション）
 *   セマンティックバージョニングなど、特定のバージョンフォーマットを強制する場合に使用
 *   例: "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$" （セマンティックバージョニング）
 * @property downloadUrlTemplate ダウンロードURLのテンプレート（オプション）
 *   カスタムダウンロードURLを指定する場合に使用
 *   プレースホルダー: {versionId}, {version}, {fileName}
 *   例: "https://api.modrinth.com/v2/project/luckperms/version/{versionId}/file"
 * @property fileNamePattern ファイル名の選択用正規表現（オプション）
 *   複数のファイルがある場合に、どのファイルをダウンロードするかを指定
 *   例: "luckperms-.*\\.jar" → "luckperms-"で始まる.jarファイルを選択
 * @property fileNameTemplate ダウンロード後のファイル名テンプレート（オプション）
 *   ダウンロードしたファイルをリネームする場合に使用
 *   プレースホルダー: <version>, <version.major>, <version.minor>, <version.patch>
 *   例: "luckperms-<version>.jar" → "luckperms-5.4.97.jar"
 */
@Serializable
data class RepositoryConfig(
    val type: String,
    @SerialName("id")
    val repositoryId: String,
    @SerialName("versionModifier")
    val versionPattern: String? = null,
    @SerialName("downloadUrl")
    val downloadUrlTemplate: String? = null,
    @SerialName("fileNameRegex")
    val fileNamePattern: String? = null,
    val fileNameTemplate: String? = null
)