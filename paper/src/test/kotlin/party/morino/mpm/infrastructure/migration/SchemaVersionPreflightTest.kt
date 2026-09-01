/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.migration

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.infrastructure.persistence.ProjectRepositoryImpl
import party.morino.mpm.infrastructure.plugin.service.PluginMetadataManagerImpl
import java.io.File

/**
 * 破壊的操作の前に版数を検査する事前判定（preflight）のテスト
 *
 * 書き込み地点のガードだけでは、JARの差し替えやJARの削除が終わってから拒否されるため、
 * 「コマンドは失敗したのにファイルだけ書き換わっている」という状態が残ってしまう。
 * その中断判断に使う副作用のない事前判定が、未来版数を正しく拒否することを検証する。
 */
class SchemaVersionPreflightTest {
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

                            override fun getPluginsDirectory(): File = File(testRoot, "plugins")

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

    /** metadataディレクトリに任意の内容のyamlを書き出す */
    private fun writeMetadata(
        name: String,
        content: String
    ): File {
        val metadataDir = File(rootDir, "metadata").apply { mkdirs() }
        return File(metadataDir, "$name.yaml").apply { writeText(content) }
    }

    @Test
    @DisplayName("Metadata preflight rejects a future version before any download")
    fun metadataPreflightRejectsFutureVersion() {
        // schemaVersion は単なるIntフィールドなので、v3でも通常の読み込みは成功してしまう。
        // 更新経路はこの事前判定でJARに触れる前に中断する
        val metadataFile = writeMetadata("Future", "schemaVersion: 3\nsomethingNew: true\n")

        val result = PluginMetadataManagerImpl().ensureMetadataReplaceable("Future")

        assertTrue(result.isLeft(), "未来版数のメタデータは破壊的操作の前に中断すべき")
        // 事前判定は副作用を持たない（原本に一切触れない）
        assertTrue(metadataFile.exists(), "事前判定はファイルを変更してはならない")
    }

    @Test
    @DisplayName("Metadata preflight allows a current version")
    fun metadataPreflightAllowsCurrentVersion() {
        writeMetadata("Current", "schemaVersion: 2\n")

        val result = PluginMetadataManagerImpl().ensureMetadataReplaceable("Current")

        assertTrue(result.isRight(), "現行版数のメタデータは通常どおり更新できるべき")
    }

    @Test
    @DisplayName("mpm.json preflight rejects a future version before the jar is deleted")
    fun projectPreflightRejectsFutureVersion() {
        // ignoreUnknownKeys のため v3 でも読み込みには成功する。
        // uninstall はこの事前判定でJARを削除する前に中断する
        val futureJson = """{"schemaVersion": 3, "name": "test", "plugins": {"Foo": "latest"}}"""
        val mpmFile = File(rootDir, "mpm.json").apply { writeText(futureJson) }

        val result = runBlocking { ProjectRepositoryImpl().ensureSavable() }

        assertTrue(result.isLeft(), "未来版数のmpm.jsonはJAR削除の前に中断すべき")
        assertTrue(mpmFile.readText() == futureJson, "事前判定はファイルを変更してはならない")
    }

    @Test
    @DisplayName("mpm.json preflight allows a current version")
    fun projectPreflightAllowsCurrentVersion() {
        File(rootDir, "mpm.json").writeText("""{"schemaVersion": 2, "name": "test", "plugins": {}}""")

        val result = runBlocking { ProjectRepositoryImpl().ensureSavable() }

        assertTrue(result.isRight(), "現行版数のmpm.jsonは通常どおり保存できるべき")
    }
}