/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin.install

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * planInstallTargets（一括インストールの計画を立てる純粋関数）のテスト
 *
 * 多段syncが1回の `mpm install` で収束すること、
 * および lock が唯一の拒否権であり続けることを主要分岐として検証する
 */
@DisplayName("planInstallTargetsのテスト")
class InstallPlannerTest {
    /** インストール候補を簡潔に作るためのヘルパー */
    private fun candidate(
        name: String,
        expected: String,
        installed: String?,
        locked: Boolean = false
    ) = InstallCandidate(
        pluginName = name,
        expectedVersion = expected,
        installedVersion = installed,
        locked = locked
    )

    @Test
    @DisplayName("Chained sync plugins are all planned in a single run")
    fun chainedSyncConvergesInOneRun() {
        // A を 2.0.0 へ上げる。B は A に、C は B に追従する（いずれも現在 1.0.0）
        val plan =
            planInstallTargets(
                listOf(
                    candidate("A", "2.0.0", installed = "1.0.0"),
                    candidate("B", "sync:A", installed = "1.0.0"),
                    candidate("C", "sync:B", installed = "1.0.0")
                )
            )

        // ディスク上の旧バージョンではなく「この実行で入る 2.0.0」で判定されるため、3つとも対象になる
        assertEquals(listOf("A", "B", "C"), plan.pluginsToInstall)
        assertEquals("2.0.0", plan.resolvedVersions["C"], "孫も親の更新後バージョンを追うべき")
    }

    @Test
    @DisplayName("Sync descendants of a latest parent are planned conservatively")
    fun dynamicParentPullsDescendants() {
        // 親が latest の場合、計画時点では着地バージョンが分からない
        val plan =
            planInstallTargets(
                listOf(
                    candidate("A", "latest", installed = "1.0.0"),
                    candidate("B", "sync:A", installed = "1.0.0")
                )
            )

        // 取りこぼさないよう子孫も対象へ倒す（実際に同期済みだった場合は実行側でスキップされる）
        assertEquals(listOf("A", "B"), plan.pluginsToInstall)
    }

    @Test
    @DisplayName("Locked parent stays on disk version and its child does not follow")
    fun lockedParentBlocksFollow() {
        // A はロック中なので 2.0.0 へ上げない。B は据え置かれた 1.0.0 と比較される
        val plan =
            planInstallTargets(
                listOf(
                    candidate("A", "2.0.0", installed = "1.0.0", locked = true),
                    candidate("B", "sync:A", installed = "1.0.0")
                )
            )

        assertEquals(listOf("A"), plan.lockedSkipped)
        assertTrue(plan.pluginsToInstall.isEmpty(), "ロック中の親も、同期済みの子も入れ替えないべき")
        assertEquals("1.0.0", plan.resolvedVersions["A"], "据え置いた親はディスク上のバージョンで確定する")
    }

    @Test
    @DisplayName("Locked sync child stops the chain at its own version")
    fun lockedChildStopsChain() {
        // A は 2.0.0 へ。B はロック中で 1.0.0 のまま。C は B に追従するので動かない
        val plan =
            planInstallTargets(
                listOf(
                    candidate("A", "2.0.0", installed = "1.0.0"),
                    candidate("B", "sync:A", installed = "1.0.0", locked = true),
                    candidate("C", "sync:B", installed = "1.0.0")
                )
            )

        assertEquals(listOf("A"), plan.pluginsToInstall)
        assertEquals(listOf("B"), plan.lockedSkipped)
        assertEquals("1.0.0", plan.resolvedVersions["C"], "ロックで止まった親に追従する孫も動かないべき")
    }

    @Test
    @DisplayName("Locked latest plugin is not reported as skipped")
    fun lockedDynamicIsNotReported() {
        // latest 指定だがロック中。据え置きが確定しており、実際には何も変わらない
        val plan =
            planInstallTargets(
                listOf(candidate("A", "latest", installed = "1.0.0", locked = true))
            )

        assertTrue(plan.pluginsToInstall.isEmpty(), "ロック中のプラグインは決して対象にしない")
        assertTrue(plan.lockedSkipped.isEmpty(), "何も変わらないのに毎回ロックスキップとして報告してはならない")
        assertEquals("1.0.0", plan.resolvedVersions["A"], "据え置いた親はディスク上のバージョンで確定する")
    }

    @Test
    @DisplayName("Already synced tree is left untouched")
    fun alreadySyncedTreeIsSkipped() {
        // すべて 2.0.0 で同期済み。再取得は不要
        val plan =
            planInstallTargets(
                listOf(
                    candidate("A", "2.0.0", installed = "2.0.0"),
                    candidate("B", "sync:A", installed = "2.0.0"),
                    candidate("C", "sync:B", installed = "2.0.0")
                )
            )

        assertTrue(plan.pluginsToInstall.isEmpty(), "同期済みのツリーは何も入れ替えないべき")
        assertTrue(plan.lockedSkipped.isEmpty())
    }
}