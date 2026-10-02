/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import party.morino.mpm.MpmTest
import party.morino.mpm.api.application.plugin.PluginLifecycleService
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.downloader.DownloaderRepository
import party.morino.mpm.api.domain.downloader.model.RepositoryType
import party.morino.mpm.api.domain.downloader.model.UrlData
import party.morino.mpm.api.domain.downloader.model.VersionData
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.domain.plugin.model.PluginSpec
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import party.morino.mpm.api.domain.project.repository.ProjectRepository
import party.morino.mpm.api.domain.repository.PluginRepositorySource
import party.morino.mpm.api.domain.repository.RepositoryConfig
import party.morino.mpm.api.domain.repository.RepositoryFile
import party.morino.mpm.api.domain.repository.RepositoryManager
import java.io.File

/**
 * addAndInstall がインストール失敗時に追加を取り消すことのテスト
 *
 * 追加だけが成功してインストールに失敗すると、JARの無いプラグインが
 * 「管理中」として残り続けてしまうため、追加前の状態へ戻すことを確かめる。
 */
@ExtendWith(MpmTest::class)
@DisplayName("addAndInstall rolls back the add when install fails")
class AddAndInstallRollbackTest : KoinComponent {
    /** バージョン解決には成功するが、ダウンロードだけ失敗するフェイク */
    private class FailingDownloader : DownloaderRepository {
        private val version = VersionData("v0.1.0", "v0.1.0")

        override fun getRepositoryType(url: String): RepositoryType? = RepositoryType.GITHUB

        override fun getUrlData(url: String): UrlData? = null

        override suspend fun getLatestVersion(urlData: UrlData): VersionData = version

        override suspend fun getVersionByName(
            urlData: UrlData,
            versionName: String
        ): VersionData = version

        override suspend fun getAllVersions(urlData: UrlData): List<VersionData> = listOf(version)

        override suspend fun downloadByVersion(
            urlData: UrlData,
            version: VersionData,
            fileNamePattern: String?
        ): File? = throw IllegalStateException("このリリースにはアセットがありません")

        @Deprecated("unused")
        override suspend fun downloadLatest(
            url: String,
            fileNamePattern: String?
        ): File? = null
    }

    /** 対象プラグインのリポジトリファイルだけを返すフェイク */
    private class SingleRepositoryManager : RepositoryManager {
        override suspend fun getAvailablePlugins(): List<String> = listOf(PLUGIN)

        override suspend fun getRepositoryFile(pluginName: String): RepositoryFile? =
            if (pluginName == PLUGIN) {
                RepositoryFile(id = PLUGIN, repositories = listOf(RepositoryConfig("github", "example/rollback-test")))
            } else {
                null
            }

        override suspend fun getAvailableSources(): List<PluginRepositorySource> = emptyList()

        override fun getRepositorySources(): List<PluginRepositorySource> = emptyList()
    }

    private val lifecycleService: PluginLifecycleService by inject()
    private val projectRepository: ProjectRepository by inject()
    private val metadataManager: PluginMetadataManager by inject()
    private val pluginDirectory: PluginDirectory by inject()

    private lateinit var mpmFile: File

    @BeforeEach
    fun setUp() {
        // ネットワークに出ないよう、リポジトリとダウンローダーをフェイクに差し替える
        loadKoinModules(
            module {
                single<RepositoryManager> { SingleRepositoryManager() }
                single<DownloaderRepository> { FailingDownloader() }
            }
        )
        mpmFile = File(pluginDirectory.getRootDirectory().apply { mkdirs() }, "mpm.json")
    }

    @AfterEach
    fun tearDown() {
        mpmFile.delete()
        metadataManager.deleteMetadata(PLUGIN)
    }

    @Test
    @DisplayName("New plugin is removed from mpm.json and metadata when install fails")
    fun newPluginIsRemovedOnInstallFailure() =
        runBlocking {
            mpmFile.writeText("""{"schemaVersion": 2, "name": "test", "plugins": {}}""")

            val result = lifecycleService.addAndInstall(PluginName(PLUGIN), VersionSpecifier.Latest)

            assertTrue(result.isLeft(), "ダウンロード失敗時は Left を返すべき")
            assertNull(projectRepository.find()?.getPluginSpec(PluginName(PLUGIN)), "mpm.json に残ってはならない")
            assertTrue(metadataManager.loadMetadata(PLUGIN).isLeft(), "メタデータが残ってはならない")
        }

    @Test
    @DisplayName("Unmanaged plugin stays unmanaged when install fails")
    fun unmanagedPluginStaysUnmanagedOnInstallFailure() =
        runBlocking {
            mpmFile.writeText("""{"schemaVersion": 2, "name": "test", "plugins": {"$PLUGIN": "unmanaged"}}""")

            val result = lifecycleService.addAndInstall(PluginName(PLUGIN), VersionSpecifier.Latest)

            assertTrue(result.isLeft(), "ダウンロード失敗時は Left を返すべき")
            assertTrue(
                projectRepository.find()?.getPluginSpec(PluginName(PLUGIN)) is PluginSpec.Unmanaged,
                "unmanaged のまま残るべき"
            )
        }

    companion object {
        private const val PLUGIN = "RollbackTarget_Test"
    }
}