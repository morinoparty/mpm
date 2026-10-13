/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

import arrow.core.Either
import arrow.core.right
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import party.morino.mpm.MpmTest
import party.morino.mpm.api.application.lock.LockService
import party.morino.mpm.api.application.plugin.PluginLifecycleService
import party.morino.mpm.api.application.plugin.PluginUpdateService
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.domain.project.lock.MpmLock
import party.morino.mpm.api.shared.error.MpmError

/**
 * 状態変更サービスがロックファイル（mpm-lock.yaml）を再生成する条件のテスト
 *
 * 再生成はコマンド層ではなくサービス層の責務であり、成功した場合にだけ走る（#448）。
 * 失敗した操作で再生成すると、実際には何も変わっていないのにロックファイルの
 * generatedAt だけが進み、「更新された」という誤った痕跡が残ってしまう。
 */
@ExtendWith(MpmTest::class)
@DisplayName("Lock file regeneration on state changes")
class LockRegenerationOnStateChangeTest : KoinComponent {
    /** regenerate() の呼び出し回数を数えるだけの [LockService] */
    private class CountingLockService : LockService {
        var regenerateCount: Int = 0
            private set

        override suspend fun regenerate(): Either<MpmError, MpmLock> {
            regenerateCount++
            return MpmLock(
                lockfileVersion = MpmLock.CURRENT_LOCKFILE_VERSION,
                generatedAt = "2026-01-01T00:00:00Z",
                plugins = emptyMap()
            ).right()
        }

        override suspend fun find(): MpmLock? = null
    }

    private val countingLockService = CountingLockService()

    private val lifecycleService: PluginLifecycleService by inject()
    private val updateService: PluginUpdateService by inject()

    /**
     * MpmTest が組み立てたKoinの [LockService] を数え上げ用に差し替える
     *
     * サービス側は `by inject()` の遅延解決なので、まだ誰も解決していないこの時点で
     * 上書きしておけば、以降の呼び出しはこのインスタンスに届く。
     */
    @BeforeEach
    fun overrideLockService() {
        loadKoinModules(
            module {
                single<LockService> { countingLockService }
            }
        )
        // 差し替えが効いていないと以降の「呼ばれていない」アサーションが空振りするため、
        // 実際に注入されるのが数え上げ用インスタンスであることをここで確かめておく
        val injected: LockService by inject()
        assertSame(countingLockService, injected, "テスト用のLockServiceに差し替えられているべき")
    }

    @Test
    @DisplayName("Failed install does not regenerate the lock file")
    fun failedInstallDoesNotRegenerate() =
        runBlocking {
            // メタデータが存在しないプラグインなので install は必ず失敗する
            val result = lifecycleService.install(PluginName("NonExistentPlugin_LockTest"))

            assertTrue(result.isLeft(), "存在しないプラグインの install は Left を返すべき")
            assertEquals(
                0,
                countingLockService.regenerateCount,
                "失敗した操作でロックファイルを再生成してはいけない"
            )
        }

    @Test
    @DisplayName("Failed update does not regenerate the lock file")
    fun failedUpdateDoesNotRegenerate() =
        runBlocking {
            val result = updateService.update(PluginName("NonExistentPlugin_LockTest"))

            assertTrue(result.isLeft(), "存在しないプラグインの update は Left を返すべき")
            assertEquals(
                0,
                countingLockService.regenerateCount,
                "失敗した操作でロックファイルを再生成してはいけない"
            )
        }
}