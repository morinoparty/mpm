/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.downloader

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import party.morino.mpm.MpmTest

/**
 * DownloaderRepositoryImplのリソース解放（issue #286）を検証するテスト
 */
@ExtendWith(MpmTest::class)
class DownloaderRepositoryImplTest {
    @Test
    @DisplayName("shutdown should be safe when no downloader is initialized")
    fun shutdownSafeWhenNoneInitialized() {
        val repository = DownloaderRepositoryImpl()
        // 一度もダウンローダーを使っていない場合、未初期化のlazyを
        // 生成せず例外も発生しないこと
        assertDoesNotThrow { repository.shutdown() }
    }
}