/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.plugin.service

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import party.morino.mpm.api.domain.config.PluginDirectory
import java.io.File

/**
 * 破損メタデータの退避（quarantine）のテスト
 *
 * 破損yamlを黙って作り直すと `lock: true` などの設定が無音で失われるため、
 * 作り直す前に原本を退避することを主要分岐として検証する
 */
class PluginMetadataQuarantineTest {
    @TempDir
    lateinit var rootDir: File

    private val manager = PluginMetadataManagerImpl()

    /** metadataディレクトリだけを一時ディレクトリに向けたKoinコンテキストを起動する */
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
        stopKoin()
    }

    /** 壊れたメタデータファイルを作成する */
    private fun writeCorruptMetadata(
        name: String,
        content: String
    ): File {
        val metadataDir = File(rootDir, "metadata")
        metadataDir.mkdirs()
        val file = File(metadataDir, "$name.yaml")
        file.writeText(content)
        return file
    }

    @Test
    @DisplayName("Moves unreadable metadata to .corrupt and leaves nothing behind")
    fun quarantineMovesFile() {
        val original = writeCorruptMetadata("Broken", "settings:\n  lock: true\n:::壊れている")

        val quarantined = manager.quarantineMetadata("Broken").getOrNull()

        // 原本は消え、内容はそのまま退避先に残る
        assertFalse(original.exists(), "原本は退避先へ移動しているべき")
        assertEquals(File(File(rootDir, "metadata"), "Broken.yaml.corrupt"), quarantined)
        assertTrue(quarantined!!.readText().contains("lock: true"), "退避先に元の内容が保存されるべき")
    }

    @Test
    @DisplayName("Uses a numbered suffix instead of overwriting an existing quarantine")
    fun quarantineDoesNotOverwrite() {
        // 1回目の退避
        writeCorruptMetadata("Broken", "first")
        manager.quarantineMetadata("Broken")

        // 2回目の退避（退避先が既に存在する）
        writeCorruptMetadata("Broken", "second")
        val second = manager.quarantineMetadata("Broken").getOrNull()

        assertEquals(File(File(rootDir, "metadata"), "Broken.yaml.corrupt.1"), second)
        // 1回目の退避ファイルは上書きされていない
        assertEquals("first", File(File(rootDir, "metadata"), "Broken.yaml.corrupt").readText())
        assertEquals("second", second!!.readText())
    }

    @Test
    @DisplayName("Returns null when there is no metadata file to quarantine")
    fun quarantineReturnsNullWhenAbsent() {
        val result = manager.quarantineMetadata("NeverInstalled")

        assertTrue(result.isRight(), "ファイルが無いのは正常系であるべき")
        assertNull(result.getOrNull(), "退避が発生しなかったことをnullで表すべき")
    }

    @Test
    @DisplayName("Refuses to quarantine a future-version metadata file")
    fun quarantineRefusesFutureVersion() {
        // strictな読み込みでは失敗するが、これは破損ではなく「このmpmでは解釈できない」だけの有効なファイル
        val original = writeCorruptMetadata("Future", "schemaVersion: 3\nsomethingNew: true\n")

        val result = manager.quarantineMetadata("Future")

        assertTrue(result.isLeft(), "未来版数のファイルは退避せず拒否すべき")
        assertTrue(original.exists(), "原本を消すとダウングレード防止ガードが迂回されるため残すべき")
    }

    @Test
    @DisplayName("Refuses to restore a file that is not this plugin's quarantine")
    fun restoreRejectsForeignFile() {
        // 別プラグインの健全なメタデータ。ディレクトリは同じなので親ディレクトリ検証だけでは通ってしまう
        val other = writeCorruptMetadata("Other", "pluginInfo:\n  name: Other\n")

        val result = manager.restoreQuarantinedMetadata("Target", other)

        assertTrue(result.isLeft(), "退避成果物でないファイルを戻し先として受け付けてはならない")
        assertTrue(other.exists(), "他プラグインのメタデータを移動して失わせてはならない")
        assertFalse(File(File(rootDir, "metadata"), "Target.yaml").exists())
    }

    @Test
    @DisplayName("Rejects path traversal plugin name")
    fun quarantineRejectsTraversal() {
        val result = manager.quarantineMetadata("../../evil")

        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.contains("不正な") == true)
    }
}