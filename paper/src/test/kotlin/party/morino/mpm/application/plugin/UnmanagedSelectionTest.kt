/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.domain.plugin.model.PluginSpec
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.domain.plugin.scan.model.InstalledJar
import java.io.File

/**
 * PluginInfoServiceImpl.selectUnmanaged（管理外JARの選別を行う純粋関数）のテスト
 */
@DisplayName("selectUnmanagedのテスト")
class UnmanagedSelectionTest {
    /** テスト用のInstalledJarを組み立てるヘルパー */
    private fun jar(
        fileName: String,
        pluginName: String
    ): InstalledJar = InstalledJar(file = File("plugins", fileName), name = pluginName, version = "1.0.0")

    /** 管理下（Managed）のプラグイン指定を作るヘルパー */
    private fun managed(name: String): Pair<String, PluginSpec> =
        name.lowercase() to PluginSpec.Managed(PluginName(name), VersionSpecifier.Fixed("1.0.0"))

    @Test
    @DisplayName("Jar managed by name is excluded regardless of case")
    fun excludesJarMatchedByName() {
        val jars = listOf(jar("alpha-1.0.0.jar", "Alpha"), jar("beta-1.0.0.jar", "Beta"))

        val result =
            PluginInfoServiceImpl.selectUnmanaged(
                installedJars = jars,
                // mpm.jsonのキーが小文字でもプラグイン名と突き合わせられる
                specsByLowerName = mapOf(managed("alpha")),
                managedJarFileNames = emptySet()
            )

        assertEquals(listOf("Beta"), result.map { it.name })
    }

    @Test
    @DisplayName("Jar managed by metadata file name is excluded even when names differ")
    fun excludesJarMatchedByFileName() {
        // mpm.jsonの管理名は "RepositorySlug"、plugin.ymlの名前は "RuntimePluginName" というズレを再現する
        val jars = listOf(jar("RepositorySlug-1.0.0.jar", "RuntimePluginName"))

        val result =
            PluginInfoServiceImpl.selectUnmanaged(
                installedJars = jars,
                specsByLowerName = mapOf(managed("RepositorySlug")),
                // メタデータに記録された配置済みJARのファイル名
                managedJarFileNames = setOf("repositoryslug-1.0.0.jar")
            )

        assertEquals(emptyList<String>(), result.map { it.name })
    }

    @Test
    @DisplayName("Unregistered and unmanaged-spec jars are reported")
    fun reportsUnregisteredAndUnmanagedSpec() {
        val jars = listOf(jar("alpha-1.0.0.jar", "Alpha"), jar("beta-1.0.0.jar", "Beta"))

        val result =
            PluginInfoServiceImpl.selectUnmanaged(
                installedJars = jars,
                // Alphaはunmanaged登録、Betaは未登録。どちらも管理外として返る
                specsByLowerName = mapOf("alpha" to PluginSpec.Unmanaged(PluginName("Alpha"))),
                managedJarFileNames = setOf("other-1.0.0.jar")
            )

        assertEquals(listOf("Alpha", "Beta"), result.map { it.name })
    }

    @Test
    @DisplayName("Stale duplicate jar does not resurrect a managed plugin")
    fun collapsesDuplicatesToManaged() {
        // 旧バージョンのJARが残っており、そちらが集約の代表に選ばれるケース
        val jars =
            listOf(
                jar("RepositorySlug-0.9.0.jar", "RuntimePluginName"),
                jar("RepositorySlug-1.0.0.jar", "RuntimePluginName")
            )

        val result =
            PluginInfoServiceImpl.selectUnmanaged(
                installedJars = jars,
                specsByLowerName = mapOf(managed("RepositorySlug")),
                managedJarFileNames = setOf("repositoryslug-1.0.0.jar")
            )

        assertEquals(emptyList<String>(), result.map { it.name })
    }
}