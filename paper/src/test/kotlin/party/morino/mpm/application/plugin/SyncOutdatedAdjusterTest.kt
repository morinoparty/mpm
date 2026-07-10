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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.application.model.outdated.OutdatedInfo

/**
 * adjustSyncOutdated（sync子の更新先を親のlatestに揃える純粋関数）のテスト
 */
@DisplayName("adjustSyncOutdatedのテスト")
class SyncOutdatedAdjusterTest {
    @Test
    @DisplayName("Sync child follows parent's latest and needsUpdate is recomputed")
    fun syncChildFollowsParentLatest() {
        // 親: 1.0.0 → 2.0.0、子: 1.0.0（自身のリポジトリでは 9.9.9 が最新だが親に追従すべき）
        val outdated =
            listOf(
                OutdatedInfo("Parent", currentVersion = "1.0.0", latestVersion = "2.0.0", needsUpdate = true),
                OutdatedInfo("Child", currentVersion = "1.0.0", latestVersion = "9.9.9", needsUpdate = true)
            )

        val adjusted = adjustSyncOutdated(outdated, mapOf("Child" to "Parent"))

        val child = adjusted.first { it.pluginName == "Child" }
        // 子の更新先は親のlatest(2.0.0)に揃う
        assertEquals("2.0.0", child.latestVersion)
        // 現在(1.0.0) != 親latest(2.0.0) なので更新が必要
        assertTrue(child.needsUpdate)
        // 親はそのまま
        assertEquals("2.0.0", adjusted.first { it.pluginName == "Parent" }.latestVersion)
    }

    @Test
    @DisplayName("Sync child already at parent version is not outdated")
    fun syncChildAlreadySynced() {
        // 親も子も 2.0.0（同期済み）。子のリポジトリlatestが新しくても更新不要と判定される
        val outdated =
            listOf(
                OutdatedInfo("Parent", currentVersion = "2.0.0", latestVersion = "2.0.0", needsUpdate = false),
                OutdatedInfo("Child", currentVersion = "2.0.0", latestVersion = "9.9.9", needsUpdate = true)
            )

        val adjusted = adjustSyncOutdated(outdated, mapOf("Child" to "Parent"))

        val child = adjusted.first { it.pluginName == "Child" }
        assertEquals("2.0.0", child.latestVersion)
        assertFalse(child.needsUpdate, "親と同期済みの子は更新不要であるべき")
    }

    @Test
    @DisplayName("Non-sync plugins and unresolved parents are left unchanged")
    fun leavesOthersUnchanged() {
        val outdated =
            listOf(
                OutdatedInfo("Normal", currentVersion = "1.0.0", latestVersion = "1.2.0", needsUpdate = true),
                // 親がリストに存在しない子はフォールバックでそのまま
                OutdatedInfo("Orphan", currentVersion = "1.0.0", latestVersion = "3.0.0", needsUpdate = true)
            )

        val adjusted = adjustSyncOutdated(outdated, mapOf("Orphan" to "MissingParent"))

        // 非同期プラグインはそのまま
        assertEquals(outdated[0], adjusted.first { it.pluginName == "Normal" })
        // 親を解決できない子もそのまま（誤った補正をしない）
        assertEquals(outdated[1], adjusted.first { it.pluginName == "Orphan" })
    }
}