/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.commands.repo

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bukkit.command.CommandSender
import org.incendo.cloud.annotations.Argument
import org.incendo.cloud.annotations.Command
import org.incendo.cloud.annotations.Permission
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.config.PluginDirectory
import party.morino.mpm.api.core.plugin.DownloaderRepository
import party.morino.mpm.api.repository.RepositoryConfig
import party.morino.mpm.api.repository.RepositoryFile
import java.io.File

/**
 * リポジトリファイルを作成するコマンドのコントローラー
 * URLからリポジトリタイプを判定し、repository/{pluginName}.jsonを生成する
 * mpm create-repo <pluginName> <url> [fileNamePattern]
 */
@Command("mpm")
@Permission("mpm.command")
class RepositoryCommands : KoinComponent {
    // KoinによるDI
    private val downloaderRepository: DownloaderRepository by inject()
    private val pluginDirectory: PluginDirectory by inject()

    // JSONシリアライザー（整形済み）
    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

    /**
     * URLからリポジトリファイルを作成するコマンド
     * @param sender コマンド送信者
     * @param pluginName プラグイン名（リポジトリファイル名として使用）
     * @param url リポジトリURL
     * @param fileNamePattern ファイル名に一致する正規表現パターン（オプション）
     */
    @Command("create-repo <pluginName> <url> [fileNamePattern]")
    suspend fun createRepo(
        sender: CommandSender,
        @Argument("pluginName") pluginName: String,
        @Argument("url") url: String,
        @Argument("fileNamePattern") fileNamePattern: String?
    ) {
        // 入力バリデーション
        if (pluginName.isEmpty()) {
            sender.sendMessage("プラグイン名を入力してください")
            return
        }
        if (url.isEmpty()) {
            sender.sendMessage("URLを入力してください")
            return
        }

        // URLからリポジトリタイプを判定
        val repositoryType = downloaderRepository.getRepositoryType(url)
        if (repositoryType == null) {
            sender.sendMessage("サポートされていないURLです: $url")
            sender.sendMessage("サポートされているリポジトリ: GitHub, Modrinth, SpigotMC")
            return
        }

        // URLからURLデータを抽出
        val urlData = downloaderRepository.getUrlData(url)
        if (urlData == null) {
            sender.sendMessage("URLからデータを抽出できませんでした: $url")
            return
        }

        // リポジトリタイプに応じてIDを取得
        val repositoryId =
            when (urlData) {
                is party.morino.mpm.api.model.repository.UrlData.GithubUrlData ->
                    "${urlData.owner}/${urlData.repository}"
                is party.morino.mpm.api.model.repository.UrlData.SpigotMcUrlData ->
                    urlData.resourceId
                is party.morino.mpm.api.model.repository.UrlData.ModrinthUrlData -> urlData.id
                else -> {
                    sender.sendMessage("未対応のリポジトリタイプです")
                    return
                }
            }

        // RepositoryConfigを作成
        val repositoryConfig =
            RepositoryConfig(
                type = repositoryType.name.lowercase(),
                repositoryId = repositoryId,
                fileNamePattern = fileNamePattern
            )

        // RepositoryFileを作成
        val repositoryFile =
            RepositoryFile(
                id = pluginName,
                website = url,
                repositories = listOf(repositoryConfig)
            )

        // repository/ディレクトリを取得
        val repositoryDir = pluginDirectory.getRepositoryDirectory()
        if (!repositoryDir.exists()) {
            repositoryDir.mkdirs()
        }

        // JSONファイルとして保存
        val outputFile = File(repositoryDir, "$pluginName.json")
        if (outputFile.exists()) {
            sender.sendMessage("既に同名のリポジトリファイルが存在します: ${outputFile.name}")
            sender.sendMessage("上書きする場合は、先にファイルを削除してください")
            return
        }

        try {
            val jsonString = json.encodeToString(repositoryFile)
            outputFile.writeText(jsonString)
            sender.sendMessage(
                "リポジトリファイルを作成しました: ${outputFile.absolutePath}"
            )
            sender.sendMessage("次のコマンドでプラグインを追加できます: /mpm add $pluginName")
        } catch (e: Exception) {
            sender.sendMessage("リポジトリファイルの作成に失敗しました: ${e.message}")
        }
    }
}