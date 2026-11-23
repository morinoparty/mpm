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
}