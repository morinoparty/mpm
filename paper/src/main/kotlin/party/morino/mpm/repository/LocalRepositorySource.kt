/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import party.morino.mpm.api.repository.PluginRepositorySource
import party.morino.mpm.api.repository.RepositoryFile
import java.io.File

/**
 * ローカルファイルシステムからリポジトリファイルを取得するソース
 * @property directory リポジトリディレクトリ
 */
class LocalRepositorySource(
    private val directory: File
) : PluginRepositorySource {
    // JSONパーサー
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * リポジトリディレクトリが存在するかを確認
     * @return ディレクトリが存在する場合はtrue
     */
    override suspend fun isAvailable(): Boolean =
        withContext(Dispatchers.IO) {
            directory.exists() && directory.isDirectory
        }

    /**
     * 利用可能なプラグインの一覧を取得
     * リポジトリディレクトリ内の.jsonファイルを検索
     * @return プラグイン名のリスト（拡張子なし）
     */
    override suspend fun getAvailablePlugins(): List<String> =
        withContext(Dispatchers.IO) {
            if (!isAvailable()) {
                return@withContext emptyList()
            }

            // ディレクトリ内の.jsonファイルを検索
            directory
                .listFiles { file ->
                    file.isFile && file.extension == "json"
                }?.map { file ->
                    // 拡張子を除いたファイル名をプラグイン名として返す
                    file.nameWithoutExtension
                }?.sorted() ?: emptyList()
        }

    /**
     * 指定したプラグインのリポジトリファイルを取得
     * @param pluginName プラグイン名
     * @return リポジトリファイルの内容、見つからない場合はnull
     */
    override suspend fun getRepositoryFile(pluginName: String): RepositoryFile? =
        withContext(Dispatchers.IO) {
            val file = File(directory, "$pluginName.json")

            // ファイルが存在しない場合はnullを返す
            if (!file.exists() || !file.isFile) {
                return@withContext null
            }

            try {
                // JSONファイルを読み込んでデシリアライズ
                val content = file.readText()
                json.decodeFromString<RepositoryFile>(content)
            } catch (e: Exception) {
                // デシリアライズに失敗した場合はnullを返す
                null
            }
        }

    /**
     * リポジトリソースの種類を取得
     * @return "local"
     */
    override fun getSourceType(): String = "local"

    /**
     * リポジトリソースの識別子を取得
     * @return ディレクトリの絶対パス
     */
    override fun getIdentifier(): String = directory.absolutePath
}