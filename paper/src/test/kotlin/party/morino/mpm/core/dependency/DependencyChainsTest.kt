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
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/**
 * DependencyAnalyzer.getDependencyChains（`mpm deps why`）のテスト
 *
 * plugin.ymlのみを含む最小JARを動的に生成し、依存チェーン（Root -> Mid -> Leaf）と
 * 循環依存を模した構成で検証する。
 */
@ExtendWith(MpmTest::class)
@DisplayName("DependencyAnalyzer.getDependencyChains")
class DependencyChainsTest : KoinComponent {
    private val dependencyAnalyzer: DependencyAnalyzer by inject()
    private val pluginDirectory: PluginDirectory by inject()

    private val createdFiles = mutableListOf<File>()

    /**
     * `name`が`depend`に依存する最小限のBukkitプラグインJARを生成する
     */
    private fun createPluginJar(
        name: String,
        depend: List<String> = emptyList()
    ) {
        val pluginsDir = pluginDirectory.getPluginsDirectory()
        if (!pluginsDir.exists()) pluginsDir.mkdirs()

        val dependYaml = if (depend.isEmpty()) "[]" else depend.joinToString(prefix = "[", postfix = "]") { it }
        val pluginYml =
            """
            name: $name
            version: '1.0'
            main: party.morino.mpm.test.$name
            api-version: '1.20'
            depend: $dependYaml
            """.trimIndent()

        val file = File(pluginsDir, "$name.jar")
        JarOutputStream(file.outputStream()).use { jar ->
            jar.putNextEntry(JarEntry("plugin.yml"))
            jar.write(pluginYml.toByteArray())
            jar.closeEntry()
        }
        createdFiles.add(file)
    }

    @AfterEach
    fun cleanup() {
        createdFiles.forEach { it.delete() }
        createdFiles.clear()
    }

    @Test
    @DisplayName("returns the full chain Root -> Mid -> Leaf for a transitive dependency")
    fun returnsFullChainForTransitiveDependency() {
        // Root depends on Mid, Mid depends on Leaf
        createPluginJar("Root", depend = listOf("Mid"))
        createPluginJar("Mid", depend = listOf("Leaf"))
        createPluginJar("Leaf")

        val result = dependencyAnalyzer.getDependencyChains("Leaf")

        assertTrue(result.isRight(), "should resolve chains for an installed plugin")
        result.onRight { chains ->
            assertEquals(1, chains.size)
            assertEquals(listOf("Root", "Mid", "Leaf"), chains.first())
        }
    }

    @Test
    @DisplayName("returns a single-element chain when the plugin is not depended on by anything")
    fun returnsSelfChainForTopLevelPlugin() {
        createPluginJar("StandaloneTop")

        val result = dependencyAnalyzer.getDependencyChains("StandaloneTop")

        assertTrue(result.isRight())
        result.onRight { chains ->
            assertEquals(listOf(listOf("StandaloneTop")), chains)
        }
    }

    @Test
    @DisplayName("returns multiple chains when depended on by more than one top-level plugin")
    fun returnsMultipleChainsForMultipleDependents() {
        // A depends on Shared, B depends on Shared
        createPluginJar("A", depend = listOf("Shared"))
        createPluginJar("B", depend = listOf("Shared"))
        createPluginJar("Shared")

        val result = dependencyAnalyzer.getDependencyChains("Shared")

        assertTrue(result.isRight())
        result.onRight { chains ->
            assertEquals(2, chains.size)
            assertTrue(chains.contains(listOf("A", "Shared")))
            assertTrue(chains.contains(listOf("B", "Shared")))
        }
    }

    @Test
    @DisplayName("terminates on a circular dependency without silently dropping the chain")
    fun terminatesOnCircularDependency() {
        // X depends on Y, Y depends on X（循環依存）
        createPluginJar("X", depend = listOf("Y"))
        createPluginJar("Y", depend = listOf("X"))

        val result = dependencyAnalyzer.getDependencyChains("X")

        assertTrue(result.isRight(), "should terminate instead of looping forever")
        result.onRight { chains ->
            // 循環境界で経路が消失せず、Y -> X のチェーンとして記録されること
            assertEquals(listOf(listOf("Y", "X")), chains)
        }
    }

    @Test
    @DisplayName("does not treat a self-dependency as a reverse dependency")
    fun ignoresSelfDependency() {
        // Selfyは自分自身に依存している（不正な設定を模す）
        createPluginJar("Selfy", depend = listOf("Selfy"))

        val result = dependencyAnalyzer.getDependencyChains("Selfy")

        assertTrue(result.isRight())
        result.onRight { chains ->
            // 自己依存は無視され、トップレベルとして扱われること
            assertEquals(listOf(listOf("Selfy")), chains)
        }
    }

    @Test
    @DisplayName("returns PluginNotFound for an uninstalled plugin")
    fun returnsErrorForUninstalledPlugin() {
        val result = dependencyAnalyzer.getDependencyChains("NoSuchPlugin")

        assertTrue(result.isLeft())
        result.onLeft { error -> assertTrue(error is DependencyError.PluginNotFound) }
    }
}