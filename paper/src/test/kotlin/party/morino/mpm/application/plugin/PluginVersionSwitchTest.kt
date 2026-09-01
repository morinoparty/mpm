/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.MpmTest
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.application.plugin.PluginUpdateService
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.shared.error.MpmError
import java.io.File

/**
 * mpm rollback / バージョン切り替え（#355・#405）のサービスメソッドのテスト
 * ネットワークを必要としないエラー経路のみを検証する
 */
@ExtendWith(MpmTest::class)
@DisplayName("PluginUpdateService - バージョン切り替え (switchVersion / rollback)")
class PluginVersionSwitchTest : KoinComponent {
    // テスト対象サービス（実装クラスをKoinで注入）
    private val updateService: PluginUpdateService by inject()
    private val infoService: PluginInfoService by inject()
    private val pluginDirectory: PluginDirectory by inject()

    @Test
    @DisplayName("switchVersion returns Left for a plugin that is not managed")
    fun switchVersionOnUnmanagedPluginReturnsLeft() =
        runBlocking {
            // mpm.jsonに存在しないプラグインは管理対象外として弾かれる
            val result = updateService.switchVersion(PluginName("NoSuchPlugin_Test"), "1.0.0")

            assertTrue(result.isLeft(), "管理対象外のプラグインは Left を返すべき")

            val error = result.leftOrNull()!!
            assertTrue(
                error is MpmError.PluginError.NotManaged ||
                    error is MpmError.PluginError.MetadataNotFound ||
                    error is MpmError.ProjectError.NotInitialized ||
                    error is MpmError.ProjectError.ConfigNotFound,
                "エラー型が想定外: ${error::class.simpleName} - ${error.message}"
            )
        }

    @Test
    @DisplayName("rollback without version returns Left when metadata is missing")
    fun rollbackWithoutVersionReturnsLeftWhenMetadataMissing() =
        runBlocking {
            // メタデータが無いと履歴を辿れないため、バージョン省略時は解決に失敗する
            val result = updateService.rollback(PluginName("NoSuchPlugin_Test"))

            assertTrue(result.isLeft(), "メタデータが無い場合は Left を返すべき")

            val error = result.leftOrNull()!!
            assertTrue(
                error is MpmError.PluginError.MetadataNotFound ||
                    error is MpmError.PluginError.VersionResolutionFailed,
                "エラー型が想定外: ${error::class.simpleName} - ${error.message}"
            )
        }

    @Test
    @DisplayName("switchVersion aborts before any backup when mpm.json has a future schema version")
    fun switchVersionAbortsOnFutureSchemaVersion() =
        runBlocking {
            // schemaVersion は単なるIntフィールドなので、未来版数(v3)の mpm.json でも読み込みは成功する。
            // 事前判定が無いと「jarとメタデータだけ新バージョンへ進み、mpm.json への書き戻しだけが失敗する」
            // という中間状態が確定的に残り、取り消せないイベント通知とバックアップまで先に走ってしまう
            val rootDir = pluginDirectory.getRootDirectory().apply { mkdirs() }
            val mpmFile = File(rootDir, "mpm.json")
            val backupsDir = File(rootDir, "backups")
            val futureJson = """{"schemaVersion": 3, "name": "test", "plugins": {"SwitchTarget_Test": "latest"}}"""
            mpmFile.writeText(futureJson)
            try {
                val result = updateService.switchVersion(PluginName("SwitchTarget_Test"), "1.0.0")

                assertTrue(result.isLeft(), "未来版数の mpm.json では切り替えを中断すべき")
                val error = result.leftOrNull()!!
                assertTrue(
                    error is MpmError.PluginError.UpdateFailed,
                    "事前判定による中断は UpdateFailed で返るべき: ${error::class.simpleName} - ${error.message}"
                )
                // 事前判定は副作用を持たない（mpm.jsonにもバックアップにも触れない）
                assertEquals(futureJson, mpmFile.readText(), "事前判定は mpm.json を変更してはならない")
                assertTrue(
                    !backupsDir.exists() || backupsDir.listFiles().isNullOrEmpty(),
                    "中止が確定している切り替えのためにバックアップを作ってはならない"
                )
            } finally {
                mpmFile.delete()
            }
        }

    @Test
    @DisplayName("getHistory returns MetadataNotFound for an unknown plugin")
    fun getHistoryReturnsMetadataNotFoundForUnknownPlugin() =
        runBlocking {
            val result = infoService.getHistory(PluginName("NoSuchPlugin_Test"))

            assertTrue(result.isLeft(), "メタデータが無いプラグインは Left を返すべき")
            assertTrue(
                result.leftOrNull() is MpmError.PluginError.MetadataNotFound,
                "MetadataNotFound が返るべき"
            )
        }
}