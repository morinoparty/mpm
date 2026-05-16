/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.domain.repository.PluginRepositorySource
import party.morino.mpm.api.domain.repository.RepositoryFile
import party.morino.mpm.infrastructure.repository.RepositoryManagerImpl
import java.io.Closeable

/**
 * RepositoryManagerImplのリソース解放（issue #286）を検証するテスト
 */
class RepositoryManagerImplTest {
    /**
     * クローズされたかを記録するテスト用のソース
     */
    private class FakeCloseableSource(
        private val id: String
    ) : PluginRepositorySource,
        Closeable {
        var closed = false
            private set

        override suspend fun isAvailable(): Boolean = false

        override suspend fun getAvailablePlugins(): List<String> = emptyList()

        override suspend fun getRepositoryFile(pluginName: String): RepositoryFile? = null

        override fun getSourceType(): String = "fake"

        override fun getIdentifier(): String = id

        override fun close() {
            closed = true
        }
    }

    @Test
    @DisplayName("reload should close old sources to prevent connection leak")
    fun reloadClosesOldSources() {
        val oldSource = FakeCloseableSource("old")
        val newSource = FakeCloseableSource("new")
        // reload時はファクトリーが新しいソースを返す
        val manager = RepositoryManagerImpl(listOf(oldSource), { listOf(newSource) })

        manager.reload()

        // 古いソースはクローズされ、新しいソースに置き換わる
        assertTrue(oldSource.closed, "old source should be closed after reload")
        assertEquals("new", manager.getRepositorySources().single().getIdentifier())
    }

    @Test
    @DisplayName("reload should not close sources reused by the factory")
    fun reloadKeepsReusedSources() {
        val reused = FakeCloseableSource("reused")
        // factoryが同一インスタンスを再利用するケース
        val manager = RepositoryManagerImpl(listOf(reused), { listOf(reused) })

        manager.reload()

        // 再利用された現役ソースはクローズされない
        assertTrue(!reused.closed, "reused source should remain open after reload")
        assertEquals("reused", manager.getRepositorySources().single().getIdentifier())
    }

    @Test
    @DisplayName("shutdown should close all current sources")
    fun shutdownClosesSources() {
        val source = FakeCloseableSource("current")
        val manager = RepositoryManagerImpl(listOf(source))

        manager.shutdown()

        assertTrue(source.closed, "current source should be closed after shutdown")
    }
}
