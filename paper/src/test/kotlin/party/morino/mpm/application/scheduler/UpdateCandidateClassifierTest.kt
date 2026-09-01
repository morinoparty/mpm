/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related
 * and neighboring rights to this software to the public domain worldwide.
 * This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.scheduler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.application.model.outdated.OutdatedInfo

@DisplayName("UpdateCandidateClassifier tests")
class UpdateCandidateClassifierTest {
    /** 更新が必要な状態のOutdatedInfoを生成するヘルパー */
    private fun outdated(name: String): OutdatedInfo =
        OutdatedInfo(
            pluginName = name,
            currentVersion = "1.0.0",
            latestVersion = "1.1.0",
            needsUpdate = true
        )

    /** 分類結果からプラグイン名のリストを取り出すヘルパー */
    private fun names(infos: List<OutdatedInfo>): List<String> = infos.map { it.pluginName }

    @Test
    @DisplayName("classifies candidates by version spec and lock state")
    fun testClassify() {
        val needsUpdate =
            listOf(
                outdated("LatestPlugin"),
                outdated("TagPlugin"),
                outdated("SyncChild"),
                outdated("LockedSyncChild"),
                outdated("FixedPlugin"),
                outdated("PatternPlugin"),
                outdated("LockedPlugin"),
                outdated("BrokenPlugin"),
                outdated("NotInMpmJson")
            )
        val specs =
            mapOf(
                "LatestPlugin" to "latest",
                "TagPlugin" to "tag:stable",
                "SyncChild" to "sync:LatestPlugin",
                "LockedSyncChild" to "sync:LatestPlugin",
                "FixedPlugin" to "1.2.3",
                "PatternPlugin" to "pattern:^1\\..*",
                "LockedPlugin" to "latest",
                "BrokenPlugin" to "latest"
            )
        val lockStateOf: (String) -> LockState = { name ->
            when (name) {
                "LockedPlugin", "LockedSyncChild" -> LockState.LOCKED
                "BrokenPlugin" -> LockState.UNKNOWN
                else -> LockState.UNLOCKED
            }
        }

        val result = UpdateCandidateClassifier.classify(needsUpdate, specs, lockStateOf)

        // 動的指定かつ非ロックのものだけが実更新の対象になる
        assertEquals(listOf("LatestPlugin", "TagPlugin"), names(result.autoUpdate))
        // sync:はロック状態で追従可否が分かれる
        assertEquals(listOf("SyncChild"), names(result.syncFollower))
        assertEquals(listOf("LockedSyncChild"), names(result.lockedSync))
        // 固定・pattern・mpm.json未記載はチェックのみ
        assertEquals(listOf("FixedPlugin", "PatternPlugin", "NotInMpmJson"), names(result.checkOnly))
        assertEquals(listOf("LockedPlugin"), names(result.locked))
        // メタデータを読めないものは誤って更新しないようunknownへ隔離する
        assertEquals(listOf("BrokenPlugin"), names(result.unknown))
    }

    @Test
    @DisplayName("lock vetoes auto update regardless of spec")
    fun testLockVetoesAutoUpdate() {
        val needsUpdate = listOf(outdated("LatestPlugin"), outdated("TagPlugin"))
        val specs = mapOf("LatestPlugin" to "latest", "TagPlugin" to "tag:stable")

        val result = UpdateCandidateClassifier.classify(needsUpdate, specs) { LockState.LOCKED }

        // lockは唯一の拒否権であり、動的指定でも自動更新対象から外れる
        assertTrue(result.autoUpdate.isEmpty())
        assertEquals(listOf("LatestPlugin", "TagPlugin"), names(result.locked))
    }

    @Test
    @DisplayName("reports empty classification when nothing needs update")
    fun testEmptyClassification() {
        val result = UpdateCandidateClassifier.classify(emptyList(), emptyMap()) { LockState.UNLOCKED }

        assertTrue(result.isEmpty)
        assertTrue(result.lockedSync.isEmpty())
    }
}