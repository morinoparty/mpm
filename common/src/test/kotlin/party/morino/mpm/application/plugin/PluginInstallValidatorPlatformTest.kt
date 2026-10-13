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
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.io.TempDir
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import party.morino.mpm.MpmTest
import party.morino.mpm.api.domain.downloader.model.TargetPlatform
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

/**
 * PluginInstallValidator のプラットフォーム互換性チェックのテスト
 *
 * 他のプラットフォーム向けの JAR は force 指定でも拒否されることを確認する。
 */
@ExtendWith(MpmTest::class)
@DisplayName("PluginInstallValidator - platform compatibility")
class PluginInstallValidatorPlatformTest {
    @Test
    @DisplayName("rejects a Velocity-only jar on Paper even with force")
    fun rejectsVelocityJarOnPaper(
        @TempDir tempDir: File
    ) = runBlocking {
        // MpmTest は TargetPlatform.PAPER を登録している
        val jar = writeJar(File(tempDir, "proxy.jar"), "velocity-plugin.json" to VELOCITY_JSON)

        val result = PluginInstallValidator().validate(jar, "proxy", force = true)

        val incompatible = assertInstanceOf(PluginInstallValidationResult.IncompatiblePlatform::class.java, result)
        assertEquals("velocity-plugin.json", incompatible.descriptor)
        assertEquals(TargetPlatform.PAPER, incompatible.platform)
    }

    @Test
    @DisplayName("on Velocity rejects a Paper jar and accepts a Velocity jar")
    fun velocityAcceptsOnlyVelocityJars(
        @TempDir tempDir: File
    ) = runBlocking {
        // 動作中のプラットフォームを Velocity に差し替える
        loadKoinModules(module { single { TargetPlatform.VELOCITY } })
        val paperJar = writeJar(File(tempDir, "paper.jar"), "paper-plugin.yml" to PAPER_YML)
        val velocityJar = writeJar(File(tempDir, "proxy.jar"), "velocity-plugin.json" to VELOCITY_JSON)
        val validator = PluginInstallValidator()

        // Paper プラグインはプロキシでは読み込まれないため拒否される
        val rejected = validator.validate(paperJar, "paper", force = true)
        val incompatible = assertInstanceOf(PluginInstallValidationResult.IncompatiblePlatform::class.java, rejected)
        assertEquals("paper-plugin.yml", incompatible.descriptor)
        assertEquals(TargetPlatform.VELOCITY, incompatible.platform)

        // 依存関係のない Velocity プラグインはそのまま通る
        assertEquals(PluginInstallValidationResult.Valid, validator.validate(velocityJar, "proxy", force = false))
    }

    /**
     * 指定したエントリだけを含むJARを書き出す
     */
    private fun writeJar(
        file: File,
        vararg entries: Pair<String, String>
    ): File {
        JarOutputStream(file.outputStream()).use { jar ->
            entries.forEach { (name, content) ->
                jar.putNextEntry(JarEntry(name))
                jar.write(content.toByteArray())
                jar.closeEntry()
            }
        }
        return file
    }

    private companion object {
        // 依存関係を持たない最小の velocity-plugin.json
        const val VELOCITY_JSON = """{"id":"proxyonly","version":"1.0.0","main":"a.b.Proxy"}"""

        // 最小の paper-plugin.yml
        const val PAPER_YML = "name: PaperOnly\nversion: 1.0.0\nmain: a.b.Paper\napi-version: '1.21'\n"
    }
}