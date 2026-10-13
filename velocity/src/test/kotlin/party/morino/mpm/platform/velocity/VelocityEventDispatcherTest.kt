/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.platform.velocity

import com.velocitypowered.api.event.EventManager
import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.proxy.ProxyServer
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.slf4j.Logger
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.velocity.event.state.PluginLockEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.RejectedExecutionException

class VelocityEventDispatcherTest {
    // Velocity のイベントマネージャーはモックし、fire の振る舞いをテストごとに差し替える
    private val eventManager = mockk<EventManager>()

    @BeforeEach
    fun setUp() {
        val proxy = mockk<ProxyServer> { every { eventManager } returns this@VelocityEventDispatcherTest.eventManager }
        startKoin {
            modules(
                module {
                    single<ProxyServer> { proxy }
                    single<Logger> { mockk(relaxed = true) }
                }
            )
        }
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
    }

    @Test
    @DisplayName("returns false when a listener denies the event")
    fun deniedEventIsReported() {
        // リスナーが結果を拒否にした状態で完了する
        every { eventManager.fire(any<PluginLockEvent>()) } answers {
            val event = firstArg<PluginLockEvent>()
            event.result = ResultedEvent.GenericResult.denied()
            CompletableFuture.completedFuture(event)
        }

        val allowed = runBlocking { VelocityEventDispatcher().firePluginLock(InstalledPlugin("example"), "1.0.0") }

        assertFalse(allowed)
    }

    @Test
    @DisplayName("treats a rejected fire during shutdown as allowed")
    fun rejectedFireIsAllowed() {
        // プロキシの停止後はスレッドプールが閉じられ、fire が例外を投げる
        every { eventManager.fire(any<PluginLockEvent>()) } throws RejectedExecutionException("terminated")

        val allowed = runBlocking { VelocityEventDispatcher().firePluginLock(InstalledPlugin("example"), "1.0.0") }

        assertTrue(allowed)
    }
}