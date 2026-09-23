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
import org.junit.jupiter.api.Assertions.assertEquals
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
import party.morino.mpm.api.domain.project.model.MpmProject
import party.morino.mpm.api.domain.project.repository.ProjectRepository
import party.morino.mpm.api.domain.repository.PluginRepositorySource
import party.morino.mpm.api.domain.repository.RepositoryConfig
import party.morino.mpm.api.domain.repository.RepositoryFile
import party.morino.mpm.api.domain.repository.RepositoryManager
import java.io.File

/**
 * 追加直後にインストールが失敗した場合、追加が取り消されることのテスト
 *
 * 取り消されないと「失敗」と表示されたのに mpm.json にだけ残り、
 * 同じ add コマンドでやり直せなくなる（AlreadyExists / スキップ扱いになる）。
 */
@ExtendWith(MpmTest::class)
@DisplayName("Rollback of add when install fails")
class AddAndInstallRollbackTest : KoinComponent {
    /** 常に同じ定義を返すだけの [RepositoryManager] */
    private class FakeRepositoryManager : RepositoryManager {
        private val file =
            RepositoryFile(
                id = PLUGIN,
                repositories = listOf(RepositoryConfig(type = "github", repositoryId = "example/$PLUGIN"))
            )

        override suspend fun getAvailablePlugins(): List<String> = listOf(PLUGIN)

        override suspend fun getRepositoryFile(pluginName: String): RepositoryFile? =
            file.takeIf { pluginName == PLUGIN }

        override suspend fun getAvailableSources(): List<PluginRepositorySource> = emptyList()

        override fun getRepositorySources(): List<PluginRepositorySource> = emptyList()
    }

    /** バージョン解決には成功するが、ダウンロードは必ず失敗する [DownloaderRepository] */
    private class DownloadFailingDownloader : DownloaderRepository {
        private val version = VersionData(downloadId = "1", version = "1.0.0")

        override fun getRepositoryType(url: String): RepositoryType? = null

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
        ): File? = null

        @Deprecated("test fake")
        override suspend fun downloadLatest(
            url: String,
            fileNamePattern: String?
        ): File? = null
    }

    private val lifecycleService: PluginLifecycleService by inject()
    private val projectRepository: ProjectRepository by inject()
    private val metadataManager: PluginMetadataManager by inject()
    private val pluginDirectory: PluginDirectory by inject()

    /** テスト用のリポジトリとダウンローダーに差し替え、空の mpm.json を用意する */
    @BeforeEach
    fun setUp() =
        runBlocking {
            loadKoinModules(
                module {
                    single<RepositoryManager> { FakeRepositoryManager() }
                    single<DownloaderRepository> { DownloadFailingDownloader() }
                }
            )
            projectRepository.save(MpmProject.create("test"))
        }

    /** テストリソース配下に書いた mpm.json とメタデータを片付ける */
    @AfterEach
    fun tearDown() {
        File(pluginDirectory.getRootDirectory(), "mpm.json").delete()
        metadataManager.deleteMetadata(PLUGIN)
    }

    @Test
    @DisplayName("New plugin is removed from mpm.json when install fails")
    fun newPluginIsRemovedOnInstallFailure() =
        runBlocking {
            val result = lifecycleService.addAndInstall(PluginName(PLUGIN), VersionSpecifier.Latest)

            // 追加は成功してインストール（ダウンロード）で失敗した経路であることを確かめる
            assertTrue(
                result.leftOrNull()?.message?.contains("Download returned null") == true,
                "インストール段階で失敗しているべき: ${result.leftOrNull()?.message}"
            )
            assertNull(projectRepository.find()?.getPluginSpec(PluginName(PLUGIN)), "mpm.json から取り消されているべき")
            assertTrue(metadataManager.loadMetadata(PLUGIN).isLeft(), "作成したメタデータも消えているべき")
        }

    @Test
    @DisplayName("Unmanaged plugin stays unmanaged when install fails")
    fun unmanagedPluginIsRestoredOnInstallFailure() =
        runBlocking {
            // unmanaged として登録済みの状態から add する
            val unmanaged = PluginSpec.Unmanaged(PluginName(PLUGIN))
            projectRepository.save(MpmProject.create("test").addPlugin(unmanaged).getOrNull()!!)

            val result = lifecycleService.addAndInstall(PluginName(PLUGIN), VersionSpecifier.Latest)

            assertTrue(result.isLeft(), "ダウンロードに失敗するので Left を返すべき")
            assertEquals(unmanaged, projectRepository.find()?.getPluginSpec(PluginName(PLUGIN)), "unmanaged に戻っているべき")
        }

    private companion object {
        const val PLUGIN = "RollbackTestPlugin"
    }
}