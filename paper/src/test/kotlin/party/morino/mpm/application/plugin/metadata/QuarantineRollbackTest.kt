/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin.metadata

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.infrastructure.plugin.service.PluginMetadataManagerImpl
import java.io.File
import java.util.logging.Logger

/**
 * 退避した原本の巻き戻し（restoreQuarantinedMetadataOrWarn）のテスト
 *
 * 退避した後に保存が失敗すると `metadata/<名前>.yaml` が不在になり、
 * ロック判定などが無音で無効化されてしまうため、確実に戻ることを検証する
 */
class QuarantineRollbackTest {
    @TempDir
    lateinit var rootDir: File

    private val manager = PluginMetadataManagerImpl()
    private val logger = Logger.getLogger(QuarantineRollbackTest::class.java.name)

    private val metadataDir: File
        get() = File(rootDir, "metadata")

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

    /** 読み込めないメタデータを用意して退避する（インストール途中の状態を再現する） */
    private fun quarantineBroken(content: String): File {
        metadataDir.mkdirs()
        File(metadataDir, "Broken.yaml").writeText(content)
        return manager.quarantineMetadata("Broken").getOrNull()!!
    }

    @Test
    @DisplayName("Restores the quarantined original when saving the new metadata fails")
    fun restoresOriginalOnSaveFailure() {
        val original = "settings:\n  lock: true\n:::壊れている"
        val quarantined = quarantineBroken(original)
        // 退避直後は現役のメタデータが存在しない（保存が失敗した瞬間の状態）
        assertTrue(!File(metadataDir, "Broken.yaml").exists())

        restoreQuarantinedMetadataOrWarn(manager, logger, "Broken", quarantined)

        // 原本が元の場所へ戻り、内容も変わっていない
        assertEquals(original, File(metadataDir, "Broken.yaml").readText())
        assertTrue(!quarantined.exists(), "戻した以上、退避先には残らないべき")
    }

    @Test
    @DisplayName("Keeps the quarantined copy and reports its path when restore fails")
    fun reportsPathWhenRestoreFails() {
        val quarantined = quarantineBroken("壊れている")
        // 元のパスに別のファイルができている場合は上書きせず失敗させる
        File(metadataDir, "Broken.yaml").writeText("別の内容")

        val note = restoreQuarantinedMetadataOrWarn(manager, logger, "Broken", quarantined)

        // 復旧の手掛かりとして退避先の絶対パスを利用者へ伝える
        assertTrue(note.contains(quarantined.absolutePath), "退避先のパスを知らせるべき")
        assertTrue(quarantined.exists(), "戻せなかった原本は退避先に残すべき")
    }

    @Test
    @DisplayName("Does nothing when no quarantine happened")
    fun noopWithoutQuarantine() {
        // 通常経路（退避が発生していない）では補足メッセージも出さない
        assertEquals("", restoreQuarantinedMetadataOrWarn(manager, logger, "Broken", null))
    }
}