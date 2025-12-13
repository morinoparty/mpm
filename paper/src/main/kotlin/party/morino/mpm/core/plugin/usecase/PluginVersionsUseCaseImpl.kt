/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.core.plugin.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.config.PluginDirectory
import party.morino.mpm.api.config.plugin.MpmConfig
import party.morino.mpm.api.core.plugin.DownloaderRepository
import party.morino.mpm.api.core.plugin.PluginVersionsUseCase
import party.morino.mpm.api.core.repository.PluginRepositorySourceManager
import party.morino.mpm.api.model.repository.UrlData
import party.morino.mpm.utils.Utils
import java.io.File

/**
 * mpm versionsコマンドに関するユースケースの実装
 * 依存性はKoinによって注入される
 */
class PluginVersionsUseCaseImpl :
    PluginVersionsUseCase,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()
    private val repositorySourceManager: PluginRepositorySourceManager by inject()
    private val downloaderRepository: DownloaderRepository by inject()

    /**
     * 指定されたプラグインの利用可能なバージョン一覧を取得する
     *
     * @param pluginName プラグイン名
     * @return 成功時はバージョンのリスト、失敗時はエラーメッセージ
     */
    override suspend fun getVersions(pluginName: String): Either<String, List<String>> {
        // rootディレクトリを取得
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "mpm.json")

        // mpm.jsonが存在しない場合はエラー
        if (!configFile.exists()) {
            return "mpm.jsonが存在しません。先に 'mpm init' を実行してください。".left()
        }

        // mpm.jsonを読み込む
        val mpmConfig =
            try {
                val jsonString = configFile.readText()
                Utils.json.decodeFromString<MpmConfig>(jsonString)
            } catch (e: Exception) {
                return "mpm.jsonの読み込みに失敗しました: ${e.message}".left()
            }

        // プラグインが管理対象に含まれているか確認
        if (!mpmConfig.plugins.containsKey(pluginName)) {
            return "プラグイン '$pluginName' は管理対象に含まれていません。".left()
        }

        // リポジトリファイルを取得
        val repositoryFile =
            repositorySourceManager.getRepositoryFile(pluginName)
                ?: return "リポジトリファイルが見つかりません: $pluginName".left()

        // リポジトリ設定から最初のリポジトリを取得
        val firstRepository =
            repositoryFile.repositories.firstOrNull()
                ?: return "リポジトリ設定が見つかりません: $pluginName".left()

        // RepositoryConfigからUrlDataを作成
        val urlData =
            when (firstRepository.type.lowercase()) {
                "github" -> {
                    // GitHub形式: "owner/repository"
                    val parts = firstRepository.repositoryId.split("/")
                    if (parts.size != 2) {
                        return "GitHubリポジトリIDの形式が不正です: ${firstRepository.repositoryId}".left()
                    }
                    UrlData.GithubUrlData(owner = parts[0], repository = parts[1])
                }

                "modrinth" -> {
                    UrlData.ModrinthUrlData(id = firstRepository.repositoryId)
                }

                "spigotmc" -> {
                    UrlData.SpigotMcUrlData(resourceId = firstRepository.repositoryId)
                }

                else -> {
                    return "未対応のリポジトリタイプです: ${firstRepository.type}".left()
                }
            }

        // すべてのバージョンを取得
        val versions =
            try {
                downloaderRepository.getAllVersions(urlData)
            } catch (e: Exception) {
                return "バージョン情報の取得に失敗しました (${firstRepository.type}: ${firstRepository.repositoryId}): ${e.message}"
                    .left()
            }

        // バージョン番号のリストを返す
        return versions.map { it.version }.right()
    }
}