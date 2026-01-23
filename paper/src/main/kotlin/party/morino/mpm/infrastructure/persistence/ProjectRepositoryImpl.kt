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

package party.morino.mpm.infrastructure.persistence

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.plugin.model.VersionSpecifierParser
import party.morino.mpm.api.domain.project.dto.MpmConfig
import party.morino.mpm.api.domain.project.model.MpmProject
import party.morino.mpm.api.domain.project.repository.ProjectRepository
import party.morino.mpm.utils.Utils
import java.io.File

/**
 * プロジェクトリポジトリの実装クラス
 *
 * mpm.jsonファイルの読み書きを担当する
 */
class ProjectRepositoryImpl :
    ProjectRepository,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()

    /**
     * プロジェクトを取得
     *
     * mpm.jsonファイルを読み込んでMpmProjectを返す
     * 存在しない場合はnullを返す
     */
    override suspend fun find(): MpmProject? {
        val configFile = getConfigFile()
        if (!configFile.exists()) {
            return null
        }

        return try {
            val jsonString = configFile.readText()
            val config = Utils.json.decodeFromString<MpmConfig>(jsonString)
            MpmProject.fromDto(config) { versionString ->
                VersionSpecifierParser.parse(versionString)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * プロジェクトを保存
     *
     * MpmProjectをMpmConfigに変換してmpm.jsonに保存する
     */
    override suspend fun save(project: MpmProject) {
        val configFile = getConfigFile()
        val sortedProject = project.withSortedPlugins()
        val config = sortedProject.toDto()
        val jsonString = Utils.json.encodeToString(MpmConfig.serializer(), config)
        configFile.writeText(jsonString)
    }

    /**
     * プロジェクトが存在するかどうかを確認
     */
    override suspend fun exists(): Boolean {
        return getConfigFile().exists()
    }

    /**
     * プロジェクトを削除
     *
     * mpm.jsonファイルを削除する
     */
    override suspend fun delete(): Boolean {
        val configFile = getConfigFile()
        return if (configFile.exists()) {
            configFile.delete()
        } else {
            false
        }
    }

    /**
     * mpm.jsonファイルのパスを取得する
     */
    private fun getConfigFile(): File {
        val rootDir = pluginDirectory.getRootDirectory()
        return File(rootDir, "mpm.json")
    }
}
