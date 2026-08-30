/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.domain.plugin.dto.version.HistoryEntryDto

/**
 * resolveRollbackTargetVersion（履歴から切り戻し先を決める純粋関数）のテスト
 */
@DisplayName("resolveRollbackTargetVersionのテスト")
class RollbackHistoryResolverTest {
    // 履歴エントリを組み立てるヘルパー（installedAt/actionは判定に影響しない）
    private fun entry(
        version: String,
        action: String = "install"
    ) = HistoryEntryDto(version = version, installedAt = "2025-01-01T00:00:00Z", action = action)

    @Test
    @DisplayName("Repeated rollbacks keep going further back in history")
    fun repeatedRollbacksGoFurtherBack() {
        // 1回目: [1.0.0, 2.0.0, 3.0.0] / current 3.0.0 -> 2.0.0
        val history = listOf(entry("1.0.0"), entry("2.0.0"), entry("3.0.0"))
        assertEquals("2.0.0", resolveRollbackTargetVersion(history, "3.0.0"))

        // 2回目: rollbackのエントリが追記された状態でも、往復せずさらに過去へ進む
        val afterRollback = history + entry("2.0.0", action = "rollback")
        assertEquals("1.0.0", resolveRollbackTargetVersion(afterRollback, "2.0.0"))
    }

    @Test
    @DisplayName("Returns null when there is nothing older than the current version")
    fun returnsNullWhenNoOlderEntry() {
        // 履歴が空・現在バージョンのみ・全て同じバージョンのいずれも切り戻し先なし
        assertNull(resolveRollbackTargetVersion(emptyList(), "1.0.0"))
        assertNull(resolveRollbackTargetVersion(listOf(entry("1.0.0")), "1.0.0"))
        assertNull(resolveRollbackTargetVersion(listOf(entry("1.0.0"), entry("1.0.0")), "1.0.0"))
    }

    @Test
    @DisplayName("Falls back to the latest differing entry when the current version is absent")
    fun fallsBackWhenCurrentVersionIsAbsent() {
        // 履歴に現在バージョンが無い場合は、末尾から遡って異なる最初のエントリを採用する
        val history = listOf(entry("1.0.0"), entry("2.0.0"))
        assertEquals("2.0.0", resolveRollbackTargetVersion(history, "3.0.0"))
    }
}