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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.MpmTest
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.application.plugin.PluginUpdateService
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.shared.error.MpmError

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