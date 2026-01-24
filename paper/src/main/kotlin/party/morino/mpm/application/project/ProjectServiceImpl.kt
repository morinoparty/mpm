/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
 * and related and neighboring rights to this software to the public domain worldwide.
 * This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.project

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.application.project.ProjectService
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.project.dto.MpmConfig
import party.morino.mpm.api.domain.project.dto.withSortedPlugins
import party.morino.mpm.api.domain.project.model.MpmProject
import party.morino.mpm.api.domain.project.repository.ProjectRepository
import party.morino.mpm.api.model.plugin.PluginData
import party.morino.mpm.api.shared.error.MpmError
import party.morino.mpm.utils.PluginDataUtils
import party.morino.mpm.utils.Utils
import java.io.File

/**
 * プロジェクト管理を行うApplication Service実装
 *
 * InitUseCaseImplから移行したロジックを直接実装
 */
class ProjectServiceImpl :
    ProjectService,
    KoinComponent {
    private val pluginDirectory: PluginDirectory by inject()
    private val projectRepository: ProjectRepository by inject()

    /**
     * プロジェクトを初期化する
     *
     * InitUseCaseImplから移行したロジック
     * rootDirectory/mpm.jsonを生成し、pluginsディレクトリ内のすべてのプラグインをunmanagedとして追加する
     *
     * @param projectName プロジェクト名
     * @param overwrite 既存のmpm.jsonを上書きするかどうか
     */
    override suspend fun init(projectName: String, overwrite: Boolean): Either<MpmError, MpmProject> =
        initializeInternal(projectName, overwrite = overwrite).fold(
            { error -> MpmError.ProjectError.InitializationFailed(error).left() },
            { MpmProject.create(projectName).right() }
        )

    /**
     * プロジェクトを初期化する（内部実装）
     *
     * @param projectName プロジェクト名
     * @param overwrite 既存のmpm.jsonを上書きするかどうか
     * @return 成功時はUnit、失敗時はエラーメッセージ
     */
    internal suspend fun initializeInternal(
        projectName: String,
        overwrite: Boolean
    ): Either<String, Unit> {
        // rootディレクトリを取得
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "mpm.json")

        // 既存のmpm.jsonが存在する場合のチェック
        if (configFile.exists() && !overwrite) {
            return "既にmpm.jsonが存在します。上書きする場合は --overwrite フラグを使用してください。".left()
        }

        // pluginsディレクトリからすべてのJARファイルを取得
        val pluginsDir = pluginDirectory.getPluginsDirectory()
        val pluginFiles =
            pluginsDir.listFiles { file ->
                // .jarファイルのみを対象にし、自分自身（MinecraftPluginManager）は除外
                file.isFile && file.extension == "jar"
            } ?: emptyArray()

        // 各JARファイルからプラグイン名を取得してunmanagedマップを作成
        val unmanagedPlugins = mutableMapOf<String, String>()
        pluginFiles.forEach { jarFile ->
            try {
                // JARファイルからプラグイン情報を取得
                val pluginData = PluginDataUtils.getPluginData(jarFile)
                if (pluginData != null) {
                    val pluginName =
                        when (pluginData) {
                            is PluginData.BukkitPluginData -> pluginData.name
                            is PluginData.PaperPluginData -> pluginData.name
                        }
                    // プラグイン名が空でない場合のみ追加
                    if (pluginName.isNotEmpty()) {
                        unmanagedPlugins[pluginName] = "unmanaged"
                    }
                }
            } catch (e: Exception) {
                // エラーが発生した場合はスキップ（無効なJARファイルの可能性）
            }
        }

        // MpmConfigを作成し、pluginsをa-Z順にソート
        val mpmConfig =
            MpmConfig(
                name = projectName,
                version = "1.0.0",
                plugins = unmanagedPlugins
            ).withSortedPlugins()

        // JSONとして保存
        return try {
            val jsonString = Utils.json.encodeToString(mpmConfig)
            configFile.writeText(jsonString)
            Unit.right()
        } catch (e: Exception) {
            "mpm.jsonの作成に失敗しました: ${e.message}".left()
        }
    }

    /**
     * 現在のプロジェクトを取得する
     *
     * mpm.jsonが存在しない場合はnullを返す
     */
    override suspend fun getProject(): MpmProject? {
        return projectRepository.find()
    }

    /**
     * プロジェクトを保存する
     *
     * ProjectRepositoryを使用してmpm.jsonに保存する
     */
    override suspend fun save(project: MpmProject): Either<MpmError, Unit> {
        return try {
            projectRepository.save(project)
            Unit.right()
        } catch (e: Exception) {
            MpmError.ProjectError.SaveFailed(e.message ?: "不明なエラー").left()
        }
    }

    /**
     * プロジェクトが初期化されているかどうかを確認する
     */
    override suspend fun isInitialized(): Boolean {
        return projectRepository.exists()
    }
}