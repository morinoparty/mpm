/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.domain.dependency

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
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.dependency.DependencyAnalyzer
import party.morino.mpm.api.model.dependency.DependencyError
import java.io.File

@ExtendWith(MpmTest::class)
@DisplayName("DependencyAnalyzerImplのテスト")
class DependencyAnalyzerImplTest : KoinComponent {
    private val dependencyAnalyzer: DependencyAnalyzer by inject()
    private val pluginDirectory: PluginDirectory by inject()

    @BeforeEach
    fun setup() {
        val pluginsDir = pluginDirectory.getPluginsDirectory()
        if (!pluginsDir.exists()) {
            pluginsDir.mkdirs()
        }

        // テスト用のJARファイルをコピー
        val testResourcesDir = File("src/test/resources")

        val bukkitPluginSource = File(testResourcesDir, "BukkitPlugin.jar")
        val bukkitPluginDest = File(pluginsDir, "BukkitPlugin.jar")
        if (bukkitPluginSource.exists()) {
            if (bukkitPluginDest.exists()) {
                bukkitPluginDest.delete()
            }
            bukkitPluginSource.copyTo(bukkitPluginDest)
        }

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
        val pluginsDir = pluginDirectory.getPluginsDirectory()
        File(pluginsDir, "BukkitPlugin.jar").delete()
        File(pluginsDir, "PaperPlugin.jar").delete()
    }

    @Test
    @DisplayName("Returns dependency info for existing plugin")
    fun testGetDependencyInfoForExistingPlugin() {
        val result = dependencyAnalyzer.getDependencyInfo("MinecraftPluginManager")

        assertTrue(result.isRight(), "Should return dependency info for existing plugin")
        result.onRight { info ->
            assertEquals("MinecraftPluginManager", info.pluginName)
        }
    }

    @Test
    @DisplayName("Returns error for non-existent plugin")
    fun testGetDependencyInfoForNonExistentPlugin() {
        val result = dependencyAnalyzer.getDependencyInfo("NonExistentPlugin")

        assertTrue(result.isLeft(), "Should return error for non-existent plugin")
        result.onLeft { error ->
            assertTrue(error is DependencyError.PluginNotFound)
        }
    }

    @Test
    @DisplayName("Builds dependency tree for existing plugin")
    fun testBuildDependencyTree() {
        val result = dependencyAnalyzer.buildDependencyTree("MinecraftPluginManager", false)

        assertTrue(result.isRight(), "Should build dependency tree for existing plugin")
        result.onRight { tree ->
            assertEquals("MinecraftPluginManager", tree.root.pluginName)
            assertTrue(tree.root.isInstalled)
        }
    }

    @Test
    @DisplayName("Returns empty map when no dependencies are missing")
    fun testCheckMissingDependenciesReturnsEmptyWhenAllSatisfied() {
        val result = dependencyAnalyzer.checkMissingDependencies("MinecraftPluginManager")

        assertTrue(result.isEmpty() || !result.containsKey("MinecraftPluginManager"))
    }

    @Test
    @DisplayName("Returns all dependency info for all plugins")
    fun testGetAllDependencyInfo() {
        val result = dependencyAnalyzer.getAllDependencyInfo()

        assertTrue(result.isNotEmpty(), "Should return dependency info for all plugins")
        assertTrue(result.containsKey("MinecraftPluginManager"))
    }

    @Test
    @DisplayName("Returns empty list for reverse dependencies when no plugins depend on it")
    fun testGetReverseDependencies() {
        val result = dependencyAnalyzer.getReverseDependencies("MinecraftPluginManager")

        assertTrue(result.isEmpty(), "MinecraftPluginManager should have no reverse dependencies")
    }
}