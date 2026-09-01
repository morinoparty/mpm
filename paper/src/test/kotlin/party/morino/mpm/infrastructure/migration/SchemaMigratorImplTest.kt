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
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.migration.SchemaMigrationOutcome
import party.morino.mpm.api.domain.migration.SchemaVersions
import party.morino.mpm.api.domain.plugin.dto.ManagedPluginDto
import party.morino.mpm.api.domain.project.dto.MpmConfig
import party.morino.mpm.infrastructure.persistence.ProjectRepositoryImpl
import party.morino.mpm.utils.Utils
import java.io.File

/**
 * SchemaMigratorImplのテスト
 *
 * 一時ディレクトリ上に v1 相当のファイルを置き、要求6の主要分岐
 * （v1移行 / 冪等 / 未来版数 / 1件失敗時の継続）を検証する
 */
class SchemaMigratorImplTest {
    @TempDir
    lateinit var rootDir: File

    private lateinit var migrator: SchemaMigratorImpl

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
        migrator = SchemaMigratorImpl()
    }

    @AfterEach
    fun tearDown() {
        // 他テストのGlobalContextを汚染しないよう、必ず停止する
        stopKoin()
    }

    // ===== テストデータ =====

    /** schemaVersion を持たない（＝v1相当の）config.json */
    private val legacyConfigJson =
        """
        {
          "repositories": [ { "type": "local", "path": "repository" } ],
          "settings": {
            "autoUpdate": false,
            "schedule": { "enabled": true, "cron": "0 4 * * *", "checkOnStartup": true, "dryRun": true }
          }
        }
        """.trimIndent()

    /** schemaVersion を持たない（＝v1相当の）metadata yaml */
    private val legacyMetadataYaml =
        """
        pluginInfo:
          name: "CarbonChat"
          version: "3.0.0"
          description: null
          main: null
          author: null
          website: null
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
            fileName: "CarbonChat-3.0.0.jar"
            url: null
            sha256: null
          settings:
            lock: true
            autoUpdate: false
            autoCheck: null
          history:
          - version: "3.0.0"
            installedAt: "2026-01-24T14:14:57.758020825Z"
            action: "add"
          versionPattern: null
          fileNamePattern: null
          fileNameTemplate: null
        """.trimIndent()

    private fun writeRoot(
        name: String,
        content: String
    ): File = File(rootDir, name).apply { writeText(content) }

    private fun writeMetadata(
        name: String,
        content: String
    ): File =
        File(rootDir, "metadata").let { dir ->
            dir.mkdirs()
            File(dir, name).apply { writeText(content) }
        }

    private fun outcomeOf(
        outcomes: List<SchemaMigrationOutcome>,
        fileName: String
    ): SchemaMigrationOutcome = outcomes.first { it.fileName == fileName }

    // ===== テスト =====

    @Test
    @DisplayName("migrates v1 config.json by removing dryRun")
    fun migratesLegacyConfigJson() {
        val configFile = writeRoot("config.json", legacyConfigJson)

        val report = runBlocking { migrator.migrateAll() }

        val outcome = outcomeOf(report.outcomes, "config.json")
        assertEquals(SchemaMigrationOutcome.Migrated("config.json", 1, SchemaVersions.CURRENT), outcome)

        // 廃止された dryRun が消え、現行版数がスタンプされている
        val migrated = configFile.readText()
        assertFalse(migrated.contains("dryRun"))
        assertTrue(migrated.contains("\"schemaVersion\": 2"))
        // 既存の設定値は保持されている
        assertTrue(migrated.contains("\"cron\": \"0 4 * * *\""))
    }

    @Test
    @DisplayName("migrates v1 mpm.json and keeps unknown keys")
    fun migratesLegacyMpmJson() {
        val mpmFile =
            writeRoot(
                "mpm.json",
                """
                { "name": "test", "version": "1.0.0", "plugins": { "Foo": "latest" }, "futureKey": "keep-me" }
                """.trimIndent()
            )

        runBlocking { migrator.migrateAll() }

        // 型付きデコードを経由しないため、mpmが知らないキーも保存される
        assertTrue(mpmFile.readText().contains("futureKey"))

        val decoded = Utils.json.decodeFromString<MpmConfig>(mpmFile.readText())
        assertEquals(SchemaVersions.CURRENT, decoded.schemaVersion)
        assertEquals(mapOf("Foo" to "latest"), decoded.plugins)
    }

    @Test
    @DisplayName("save keeps current schema version after migration")
    fun saveKeepsCurrentSchemaVersion() {
        // MpmProject は schemaVersion を保持しないため、save() でスタンプしないと
        // マイグレート済みの mpm.json がレガシー版数に巻き戻ってしまう
        writeRoot("mpm.json", """{ "name": "test", "plugins": { "Foo": "latest" } }""")
        runBlocking { migrator.migrateAll() }

        val repository = ProjectRepositoryImpl()
        runBlocking { repository.save(repository.find()!!) }

        val decoded = Utils.json.decodeFromString<MpmConfig>(File(rootDir, "mpm.json").readText())
        assertEquals(SchemaVersions.CURRENT, decoded.schemaVersion)
        assertEquals(mapOf("Foo" to "latest"), decoded.plugins)
    }

    @Test
    @DisplayName("stamps schema version on legacy metadata yaml")
    fun migratesLegacyMetadataYaml() {
        val metadataFile = writeMetadata("CarbonChat.yaml", legacyMetadataYaml)

        val report = runBlocking { migrator.migrateAll() }

        assertEquals(
            SchemaMigrationOutcome.Migrated("metadata/CarbonChat.yaml", 1, SchemaVersions.CURRENT),
            outcomeOf(report.outcomes, "metadata/CarbonChat.yaml")
        )

        val decoded = Yaml.default.decodeFromString(ManagedPluginDto.serializer(), metadataFile.readText())
        assertEquals(SchemaVersions.CURRENT, decoded.schemaVersion)
        // 既存フィールドが失われていない
        assertEquals("CarbonChat", decoded.pluginInfo.name)
        assertEquals(true, decoded.mpmInfo.settings.lock)
        assertEquals(1, decoded.mpmInfo.history.size)
    }

    @Test
    @DisplayName("leaves current-version files untouched and is idempotent")
    fun isIdempotent() {
        writeRoot("config.json", legacyConfigJson)
        writeMetadata("CarbonChat.yaml", legacyMetadataYaml)

        // 1回目で移行される
        runBlocking { migrator.migrateAll() }
        val configBytes = File(rootDir, "config.json").readBytes()
        val metadataBytes = File(rootDir, "metadata/CarbonChat.yaml").readBytes()

        // 2回目は現行版数なので一切書き込まれない
        val report = runBlocking { migrator.migrateAll() }

        assertTrue(outcomeOf(report.outcomes, "config.json") is SchemaMigrationOutcome.AlreadyCurrent)
        assertTrue(outcomeOf(report.outcomes, "metadata/CarbonChat.yaml") is SchemaMigrationOutcome.AlreadyCurrent)
        assertEquals(0, report.migratedCount)
        assertArrayEquals(configBytes, File(rootDir, "config.json").readBytes())
        assertArrayEquals(metadataBytes, File(rootDir, "metadata/CarbonChat.yaml").readBytes())
    }

    @Test
    @DisplayName("does not downgrade a future schema version")
    fun doesNotDowngradeFutureVersion() {
        val futureJson = """{"schemaVersion": 99, "name": "test", "unknownFutureKey": true}"""
        val mpmFile = writeRoot("mpm.json", futureJson)
        val metadataFile = writeMetadata("Future.yaml", "schemaVersion: 99\nsomethingNew: true\n")

        val report = runBlocking { migrator.migrateAll() }

        assertEquals(
            SchemaMigrationOutcome.FutureVersion("mpm.json", 99),
            outcomeOf(report.outcomes, "mpm.json")
        )
        assertEquals(
            SchemaMigrationOutcome.FutureVersion("metadata/Future.yaml", 99),
            outcomeOf(report.outcomes, "metadata/Future.yaml")
        )
        // 内容は一切変わっていない
        assertEquals(futureJson, mpmFile.readText())
        assertEquals("schemaVersion: 99\nsomethingNew: true\n", metadataFile.readText())
    }

    @Test
    @DisplayName("keeps migrating other files when one file is broken")
    fun continuesWhenOneFileIsBroken() {
        val brokenFile = writeRoot("mpm.json", "{ this is not json")
        writeRoot("config.json", legacyConfigJson)

        val report = runBlocking { migrator.migrateAll() }

        assertTrue(outcomeOf(report.outcomes, "mpm.json") is SchemaMigrationOutcome.Failed)
        assertTrue(outcomeOf(report.outcomes, "config.json") is SchemaMigrationOutcome.Migrated)
        assertEquals(1, report.migratedCount)
        assertEquals(1, report.failures.size)
        // 壊れたファイルは触られていない
        assertEquals("{ this is not json", brokenFile.readText())
    }

    @Test
    @DisplayName("reports absent files without touching the disk")
    fun reportsAbsentFiles() {
        val report = runBlocking { migrator.migrateAll() }

        assertTrue(outcomeOf(report.outcomes, "mpm.json") is SchemaMigrationOutcome.Absent)
        assertTrue(outcomeOf(report.outcomes, "config.json") is SchemaMigrationOutcome.Absent)
        assertEquals(0, report.migratedCount)
        assertFalse(File(rootDir, "mpm.json").exists())
    }
}