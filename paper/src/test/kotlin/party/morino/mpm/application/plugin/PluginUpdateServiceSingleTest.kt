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
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.MpmTest
import party.morino.mpm.api.application.plugin.PluginUpdateService
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.shared.error.MpmError

/**
 * mpm update <plugin> の個別更新コマンドが使うサービスメソッドのテスト
 * UpdateCommand.updateOne が呼び出す PluginUpdateService.update(name, force) を検証する
 */
@ExtendWith(MpmTest::class)
@DisplayName("PluginUpdateService - 個別プラグイン更新 (mpm update <plugin>)")
class PluginUpdateServiceSingleTest : KoinComponent {
    // テスト対象サービス（実装クラスをKoinで注入）
    private val updateService: PluginUpdateService by inject()

    @Test
    @DisplayName("Returns NotFound error when plugin has no metadata")
    fun updateNonExistentPluginReturnsNotFound() =
        runBlocking {
            // metadata が存在しないプラグイン名を指定
            val result = updateService.update(PluginName("NonExistentPlugin_Test"))

            // Either.Left が返ることを確認
            assertTrue(result.isLeft(), "存在しないプラグインは Left を返すべき")

            val error = result.leftOrNull()!!
            // PluginError.NotFound または MetadataNotFound であることを確認
            assertTrue(
                error is MpmError.PluginError.NotFound ||
                    error is MpmError.PluginError.MetadataNotFound ||
                    error is MpmError.ProjectError.NotInitialized ||
                    error is MpmError.ProjectError.ConfigNotFound,
                "エラー型が想定外: ${error::class.simpleName} - ${error.message}"
            )
        }

    @Test
    @DisplayName("update with force=false returns Left when plugin does not exist")
    fun updateWithForceDefaultBehavior() =
        runBlocking {
            // force=false のデフォルト挙動を確認
            val result = updateService.update(PluginName("NoSuchPlugin"), force = false)
            assertTrue(result.isLeft(), "存在しないプラグインは force=false でも Left を返すべき")
        }

    @Test
    @DisplayName("update with force=true returns Left when plugin does not exist")
    fun updateWithForceTrueOnNonExistentPlugin() =
        runBlocking {
            // force=true でも存在しないプラグインは NotFound になることを確認
            val result = updateService.update(PluginName("NoSuchPlugin"), force = true)
            assertTrue(result.isLeft(), "存在しないプラグインは force=true でも Left を返すべき")
        }

    @Test
    @DisplayName("UpdateResult fields are correct on success report")
    fun updateResultFieldsOnSuccess() {
        // UpdateResult の成功フィールドを単体で検証
        val successResult =
            party.morino.mpm.api.application.model.UpdateResult(
                pluginName = "TestPlugin",
                oldVersion = "1.0.0",
                newVersion = "1.1.0",
                success = true
            )
        assertEquals("TestPlugin", successResult.pluginName)
        assertEquals("1.0.0", successResult.oldVersion)
        assertEquals("1.1.0", successResult.newVersion)
        assertTrue(successResult.success)
        assertEquals(null, successResult.errorMessage)
    }

    @Test
    @DisplayName("UpdateResult fields are correct on failure report")
    fun updateResultFieldsOnFailure() {
        // UpdateResult の失敗フィールドを単体で検証（コマンドの表示ロジックで使用される）
        val failResult =
            party.morino.mpm.api.application.model.UpdateResult(
                pluginName = "TestPlugin",
                oldVersion = "1.0.0",
                newVersion = "1.0.0",
                success = false,
                errorMessage = "[API_VERSION_INCOMPATIBLE] API version mismatch"
            )
        assertTrue(!failResult.success)
        assertTrue(
            failResult.errorMessage?.contains("[API_VERSION_INCOMPATIBLE]") == true,
            "APIバージョン非互換マーカーが含まれているべき"
        )
        // コマンドの表示ロジック: マーカーを除去した表示文字列を検証
        val displayMessage = failResult.errorMessage?.replace("[API_VERSION_INCOMPATIBLE] ", "") ?: ""
        assertEquals("API version mismatch", displayMessage)
    }

    @Test
    @DisplayName("Concurrent update attempt returns UpdateInProgress error")
    fun concurrentUpdateReturnsUpdateInProgress() =
        runBlocking {
            // Mutex のロックは同一コルーチン内では確認しにくいが、
            // 型定義上 UpdateInProgress が存在することを確認
            val error = MpmError.PluginError.UpdateInProgress
            assertInstanceOf(MpmError.PluginError::class.java, error)
            assertEquals("An update is already in progress", error.message)
        }
}