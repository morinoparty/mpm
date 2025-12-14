/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.core.plugin

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.MpmTest
import party.morino.mpm.api.config.PluginDirectory
import party.morino.mpm.api.config.plugin.MpmConfig
import party.morino.mpm.api.core.plugin.InitUseCase
import java.io.File

@ExtendWith(MpmTest::class)
@DisplayName("InitUseCaseImplのテスト")
class InitUseCaseImplTest : KoinComponent {
    private val initUseCase: InitUseCase by inject()
    private val pluginDirectory: PluginDirectory by inject()

    private val json =
        Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        }

    @BeforeEach
    fun setup() {
        // テスト用のディレクトリを作成
        val rootDir = pluginDirectory.getRootDirectory()
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }

        // テスト用のディレクトリをクリーンアップ
        val configFile = File(rootDir, "mpm.json")
        if (configFile.exists()) {
            configFile.delete()
        }

        // テスト用のpluginsディレクトリを作成
        val pluginsDir = pluginDirectory.getPluginsDirectory()
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs()
        }

        // テスト用のプラグインJARファイルをpluginsディレクトリにコピー
        val testResourcesDir = File("src/test/resources")

        // BukkitPlugin.jarをコピー（常に上書き）
        val bukkitPluginSource = File(testResourcesDir, "BukkitPlugin.jar")
        val bukkitPluginDest = File(pluginsDir, "BukkitPlugin.jar")
        if (bukkitPluginSource.exists()) {
            if (bukkitPluginDest.exists()) {
                bukkitPluginDest.delete()
            }
            bukkitPluginSource.copyTo(bukkitPluginDest)
        }

        // PaperPlugin.jarをコピー（常に上書き）
        val paperPluginSource = File(testResourcesDir, "PaperPlugin.jar")
        val paperPluginDest = File(pluginsDir, "PaperPlugin.jar")
        if (paperPluginSource.exists()) {
            if (paperPluginDest.exists()) {
                paperPluginDest.delete()
            }
            paperPluginSource.copyTo(paperPluginDest)
        }
    }

    @AfterEach
    fun cleanup() {
        // テスト後のクリーンアップ
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "mpm.json")
        if (configFile.exists()) {
            configFile.delete()
        }

        // テスト用にコピーしたJARファイルを削除
        val pluginsDir = pluginDirectory.getPluginsDirectory()
        File(pluginsDir, "BukkitPlugin.jar").delete()
        File(pluginsDir, "PaperPlugin.jar").delete()
    }

    @Test
    @DisplayName("Creates mpm.json with unmanaged plugins when plugins exist")
    suspend fun testInitializeCreatesConfigWithPlugins() {
        // Execute test
        val result = initUseCase.initialize("test-project", overwrite = false)

        // Verify success
        assertTrue(result.isRight(), "Initialize should succeed")

        // Verify mpm.json is created
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "mpm.json")
        assertTrue(configFile.exists(), "mpm.json should be created")

        // Verify mpm.json content
        val configContent = configFile.readText()
        val mpmConfig = json.decodeFromString<MpmConfig>(configContent)

        // Verify project name
        assertEquals("test-project", mpmConfig.name, "Project name should be test-project")

        // Verify plugins map
        assertTrue(
            mpmConfig.plugins.containsKey("MinecraftPluginManager"),
            "MinecraftPluginManager should be in plugins map"
        )

        // Verify all plugins are unmanaged
        assertEquals(
            "unmanaged",
            mpmConfig.plugins["MinecraftPluginManager"],
            "MinecraftPluginManager should be unmanaged"
        )
    }

    @Test
    @DisplayName("Returns error when mpm.json exists without overwrite flag")
    suspend fun testInitializeFailsWhenConfigExists() {
        // Create existing mpm.json
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "mpm.json")
        configFile.writeText("{}")

        // Execute test
        val result = initUseCase.initialize("test-project", overwrite = false)

        // Verify failure
        assertTrue(result.isLeft(), "Initialize should fail when mpm.json already exists")

        // Verify error message
        result.onLeft { errorMessage ->
            assertTrue(
                errorMessage.contains("既にmpm.jsonが存在します"),
                "Error message should mention that mpm.json already exists"
            )
        }
    }

    @Test
    @DisplayName("Overwrites existing mpm.json when overwrite flag is true")
    suspend fun testInitializeOverwritesExistingConfig() {
        // Create existing mpm.json
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "mpm.json")
        val oldConfig =
            MpmConfig(
                name = "old-project",
                version = "1.0.0",
                plugins = mapOf("OldPlugin" to "unmanaged")
            )
        configFile.writeText(json.encodeToString(MpmConfig.serializer(), oldConfig))

        // Execute test
        val result = initUseCase.initialize("new-project", overwrite = true)

        // Verify success
        assertTrue(result.isRight(), "Initialize should succeed with overwrite flag")

        // Verify mpm.json content
        val configContent = configFile.readText()
        val mpmConfig = json.decodeFromString<MpmConfig>(configContent)

        // Verify new project name is set
        assertEquals("new-project", mpmConfig.name, "Project name should be updated to new-project")

        // Verify old plugin is removed and new plugin is added
        assertTrue(!mpmConfig.plugins.containsKey("OldPlugin"), "OldPlugin should not be in plugins map")
        assertTrue(
            mpmConfig.plugins.containsKey("MinecraftPluginManager"),
            "MinecraftPluginManager should be in plugins map"
        )
    }
}