/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.config

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
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
import party.morino.mpm.api.domain.config.model.ConfigData
import java.io.File

/**
 * config.json の読み込みが起動を止めないことのテスト
 *
 * スキーマ移行は「1ファイルの失敗で起動全体を止めない」方針であり、
 * その直後に走る設定の読み込みだけが例外を漏らして onEnable を落とすことがあってはならない。
 */
class ConfigManagerImplTest {
    @TempDir
    lateinit var tempDir: File

    // テストごとに mpm のルートディレクトリを差し替えられるようにする
    // （存在しないディレクトリを指して、保存側の失敗も検証するため）
    private lateinit var configRoot: File

    @BeforeEach
    fun setUp() {
        configRoot = tempDir
        startKoin {
            modules(
                module {
                    single<PluginDirectory> {
                        object : PluginDirectory {
                            override fun getRootDirectory(): File = configRoot

                            override fun getPluginsDirectory(): File = File(configRoot, "plugins")

                            override fun getMetadataDirectory(): File = File(configRoot, "metadata")

                            override fun getRepositoryDirectory(): File = File(configRoot, "repository")

                            override fun getBackupsDirectory(): File = File(configRoot, "backups")

                            override fun getCacheDirectory(): File = File(configRoot, "cache")
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

    @Test
    @DisplayName("Falls back to defaults when config.json cannot be read")
    fun fallsBackWhenConfigIsUnreadable() {
        // config.json と同名のディレクトリを置くと readText が例外を投げる。
        // 読み取り権限のような環境差に左右されず「読めないファイル」を再現できる
        val configFile = File(tempDir, "config.json").apply { mkdirs() }

        val manager = ConfigManagerImpl()
        runBlocking { manager.reload() }

        // 例外が漏れずデフォルト設定で起動できている
        assertEquals(ConfigData().settings, manager.getConfig().settings)
        // 読めないだけのファイルを勝手に作り直したりはしない
        assertTrue(configFile.isDirectory, "読み込み失敗時にファイルへ手を加えてはならない")
        // `/mpm reload` が緑の成功表示を出さないよう、フォールバックしたことを呼び出し側へ伝える
        assertTrue(
            manager.lastLoadFailure?.contains(configFile.absolutePath) == true,
            "フォールバックした理由と対象パスを診断情報として残すべき"
        )
    }

    @Test
    @DisplayName("Falls back to defaults when config.json cannot be created")
    fun fallsBackWhenConfigCannotBeCreated() {
        // ルートディレクトリが存在しない場合、既定値の書き出しに失敗する
        configRoot = File(tempDir, "missing-root")

        val manager = ConfigManagerImpl()
        runBlocking { manager.reload() }

        assertEquals(ConfigData().settings, manager.getConfig().settings)
    }

    @Test
    @DisplayName("Loads a valid config.json as written")
    fun loadsValidConfig() {
        File(tempDir, "config.json").writeText(
            """{"schemaVersion": 2, "repositories": [], "settings": {}}"""
        )

        val manager = ConfigManagerImpl()
        runBlocking { manager.reload() }

        // フォールバックが正常な設定まで飲み込んでいないことを確認する
        assertTrue(manager.getConfig().repositories.isEmpty())
        assertEquals(2, manager.getConfig().schemaVersion)
        // 正常に読めた場合はフォールバックの警告を残さない
        assertNull(manager.lastLoadFailure)
    }
}
