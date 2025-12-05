/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.core.plugin

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.config.PluginDirectory
import party.morino.mpm.api.config.plugin.MpmConfig
import party.morino.mpm.api.core.plugin.AddPluginUseCase
import party.morino.mpm.api.core.plugin.DownloaderRepository
import party.morino.mpm.api.core.repository.PluginRepositorySourceManager
import party.morino.mpm.api.model.repository.UrlData
import party.morino.mpm.api.utils.DataClassReplacer.replaceTemplate
import party.morino.mpm.utils.Utils
import java.io.File

/**
 * mpm addコマンドに関するユースケースの実装
 * 依存性はKoinによって注入される
 */
class AddPluginUseCaseImpl :
    AddPluginUseCase,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()
    private val repositorySourceManager: PluginRepositorySourceManager by inject()
    private val downloaderRepository: DownloaderRepository by inject()

    /**
     * プラグインを管理対象に追加する
     * mpm.jsonのpluginsマップにプラグインを追加する
     *
     * @param pluginName プラグイン名
     * @param version バージョン文字列（デフォルトは"latest"）
     * @return 成功時はUnit、失敗時はエラーメッセージ
     */
    override suspend fun addPlugin(
        pluginName: String,
        version: String
    ): Either<String, Unit> {
        // rootディレクトリを取得
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "mpm.json")

        // mpm.jsonが存在しない場合はエラー
        if (!configFile.exists()) {
            return "mpm.jsonが存在しません。先に 'mpm init' を実行してください。".left()
        }

        // リポジトリソースからプラグインが存在するか確認
        val repositoryFile = repositorySourceManager.getRepositoryFile(pluginName)

        if (repositoryFile == null) {
            return "リポジトリファイルが見つかりません: $pluginName\n利用可能なリポジトリソースで '$pluginName.json' を検索しましたが見つかりませんでした。"
                .left()
        }

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

        // 最新バージョンを取得
        val latestVersion =
            try {
                downloaderRepository.getLatestVersion(urlData)
            } catch (e: Exception) {
                return "バージョン情報の取得に失敗しました (${firstRepository.type}: ${firstRepository.repositoryId}): ${e.message}"
                    .left()
            }

        // プラグインをダウンロード
        val downloadedFile =
            try {
                downloaderRepository.downloadByVersion(
                    urlData,
                    latestVersion,
                    firstRepository.fileNamePattern
                )
            } catch (e: Exception) {
                return "プラグインのダウンロードに失敗しました (${firstRepository.type}: ${firstRepository.repositoryId}): ${e.message}"
                    .left()
            }

        if (downloadedFile == null) {
            return "プラグインファイルのダウンロードに失敗しました (${firstRepository.type}: ${firstRepository.repositoryId})。".left()
        }

        // ファイル名を生成（fileNameTemplateを使用、nullの場合はデフォルトテンプレート）
        val template = firstRepository.fileNameTemplate ?: "<pluginName>-<version.current.edited_version>.jar"
        val fileName = generateFileName(template, pluginName, latestVersion.version)

        // ダウンロードしたファイルをpluginsディレクトリに移動
        val pluginsDir = pluginDirectory.getPluginsDirectory()
        val targetFile = File(pluginsDir, fileName)
        try {
            downloadedFile.copyTo(targetFile, overwrite = true)
            downloadedFile.delete() // 一時ファイルを削除
        } catch (e: Exception) {
            return "プラグインファイルの移動に失敗しました: ${e.message}".left()
        }

        // mpm.jsonを読み込む
        val mpmConfig =
            try {
                val jsonString = configFile.readText()
                Utils.json.decodeFromString<MpmConfig>(jsonString)
            } catch (e: Exception) {
                return "mpm.jsonの読み込みに失敗しました: ${e.message}".left()
            }

        // 既に追加されているか確認（unmanagedの場合は除外）
        if (mpmConfig.plugins.containsKey(pluginName) && mpmConfig.plugins[pluginName] != "unmanaged") {
            return "プラグイン '$pluginName' は既に管理対象に追加されています。".left()
        }

        // pluginsマップに追加（バージョン番号を使用）
        val updatedPlugins = mpmConfig.plugins.toMutableMap()
        updatedPlugins[pluginName] = latestVersion.version

        // 更新されたMpmConfigを作成
        val updatedConfig = mpmConfig.copy(plugins = updatedPlugins)

        // JSONとして保存
        return try {
            val jsonString = Utils.json.encodeToString(updatedConfig)
            configFile.writeText(jsonString)
            Unit.right()
        } catch (e: Exception) {
            "mpm.jsonの更新に失敗しました: ${e.message}".left()
        }
    }

    /**
     * ファイル名テンプレートからファイル名を生成
     * DataClassReplacerを使用してプレースホルダーを実際の値で置き換える
     *
     * @param template ファイル名テンプレート
     * @param pluginName プラグイン名
     * @param versionString バージョン文字列
     * @return 生成されたファイル名
     */
    private fun generateFileName(
        template: String,
        pluginName: String,
        versionString: String
    ): String {
        // バージョンをパースしてセマンティックバージョニング形式の各部分を取得
        val versionParts = versionString.split(".", "-", "_").filter { it.isNotEmpty() }
        val major = versionParts.getOrNull(0) ?: "0"
        val minor = versionParts.getOrNull(1) ?: "0"
        val patch = versionParts.getOrNull(2) ?: "0"

        // ファイル名生成用のデータクラス（定義順序に注意）
        data class CurrentVersion(
            val edited_version: String
        )

        data class VersionData(
            val major: String,
            val minor: String,
            val patch: String,
            val current: CurrentVersion
        )

        data class FileNameData(
            val pluginName: String,
            val version: VersionData
        )

        // データクラスを作成
        val data = FileNameData(
            pluginName = pluginName,
            version = VersionData(
                major = major,
                minor = minor,
                patch = patch,
                current = CurrentVersion(edited_version = versionString)
            )
        )

        // DataClassReplacerを使用してプレースホルダーを置き換え
        return template.replaceTemplate(data)
    }
}