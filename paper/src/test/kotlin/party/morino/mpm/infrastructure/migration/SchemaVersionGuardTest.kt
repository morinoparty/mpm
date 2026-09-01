/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.migration

import com.charleskorn.kaml.Yaml
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.plugin.dto.ManagedPluginDto
import party.morino.mpm.infrastructure.persistence.ProjectRepositoryImpl
import party.morino.mpm.infrastructure.plugin.service.PluginMetadataManagerImpl
import java.io.File
import java.io.IOException

/**
 * 未来のスキーマ版数で書かれたファイルを、通常の保存経路が巻き戻さないことのテスト
 *
 * マイグレータは未来版数を触らないが、保存経路が無条件に現行版数をスタンプすると
 * そこからダウングレードが起きてしまうため、書き込み拒否を主要分岐として検証する
 */
class SchemaVersionGuardTest {
    @TempDir
    lateinit var rootDir: File

    /** テスト用にPluginDirectoryだけを差し替えたKoinコンテキストを起動する */
    @BeforeEach
    fun setUp() {
        val testRoot = rootDir
        startKoin {
            modules(
                module {
                    single<PluginDirectory> {
                        object : PluginDirectory {
                            override fun getRootDirectory(): File = testRoot

                            override fun getPluginsDirectory(): File = testRoot.parentFile

                            override fun getMetadataDirectory(): File = File(testRoot, "metadata")

                            override fun getRepositoryDirectory(): File = File(testRoot, "repository")

                            override fun getBackupsDirectory(): File = File(testRoot, "backups")

                            override fun getCacheDirectory(): File = File(testRoot, "cache")
                        }
                    }
                }
            )
        }
    }

    @AfterEach
    fun tearDown() {
        // 他テストのGlobalContextを汚染しないよう、必ず停止する
        stopKoin()
    }

    /** 現行版数(v2)で書かれたメタデータ。saveMetadata に渡すDTOの生成にも使う */
    private val currentMetadataYaml =
        """
        schemaVersion: 2
        pluginInfo:
          name: "CarbonChat"
          version: "3.0.0"
        mpmInfo:
          repository:
            type: "MODRINTH"
            id: "QzooIsZI"
          version:
            current:
              raw: "3.0.0"
              normalized: "3.0.0"
            latest:
              raw: "3.0.0"
              normalized: "3.0.0"
            lastChecked: "2026-01-24T14:14:57.983102138Z"
          download:
            downloadId: "6gfp1kIe"
          settings:
            lock: false
            autoUpdate: false
          history: []
        """.trimIndent()

    @Test
    @DisplayName("save does not downgrade a future-version mpm.json")
    fun saveRefusesFutureVersionMpmJson() {
        // ignoreUnknownKeys のため v3 でも読み込み自体は成功してしまう。
        // ガードが無いと保存時に v2 へ巻き戻り futureKey が消える
        val futureJson =
            """{"schemaVersion": 3, "name": "test", "plugins": {"Foo": "latest"}, "futureKey": "keep-me"}"""
        val mpmFile = File(rootDir, "mpm.json").apply { writeText(futureJson) }

        val repository = ProjectRepositoryImpl()
        val project = runBlocking { repository.find()!! }

        // 無音で成功扱いにすると呼び出し側が操作の成功を報告してしまうため、失敗として送出される
        assertThrows(IOException::class.java) { runBlocking { repository.save(project) } }

        // 1バイトも書き換わっていない
        assertEquals(futureJson, mpmFile.readText())
    }

    @Test
    @DisplayName("saveMetadata rejects a future-version metadata file")
    fun saveMetadataRefusesFutureVersion() {
        val metadataDir = File(rootDir, "metadata").apply { mkdirs() }
        val futureYaml = "schemaVersion: 3\nsomethingNew: true\n"
        val metadataFile = File(metadataDir, "CarbonChat.yaml").apply { writeText(futureYaml) }
        val metadata = Yaml.default.decodeFromString(ManagedPluginDto.serializer(), currentMetadataYaml)

        val result = PluginMetadataManagerImpl().saveMetadata("CarbonChat", metadata)

        assertTrue(result.isLeft())
        assertEquals(futureYaml, metadataFile.readText())
    }

    @Test
    @DisplayName("saveMetadata still writes a current-version metadata file")
    fun saveMetadataWritesCurrentVersion() {
        val metadataDir = File(rootDir, "metadata").apply { mkdirs() }
        val metadataFile = File(metadataDir, "CarbonChat.yaml").apply { writeText(currentMetadataYaml) }
        val metadata =
            Yaml.default
                .decodeFromString(ManagedPluginDto.serializer(), currentMetadataYaml)
                .let { it.copy(pluginInfo = it.pluginInfo.copy(version = "3.1.0")) }

        val result = PluginMetadataManagerImpl().saveMetadata("CarbonChat", metadata)

        assertTrue(result.isRight())
        val saved = Yaml.default.decodeFromString(ManagedPluginDto.serializer(), metadataFile.readText())
        assertEquals("3.1.0", saved.pluginInfo.version)
    }
}