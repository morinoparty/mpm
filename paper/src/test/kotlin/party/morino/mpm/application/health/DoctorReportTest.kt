/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.health

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.application.health.DoctorReport
import party.morino.mpm.api.application.model.outdated.OutdatedInfo

/**
 * DoctorReport.hasProblems の分類（更新・管理外は情報扱い）を検証する
 */
@DisplayName("DoctorReport.hasProblems")
class DoctorReportTest {
    private fun report(
        missingDependencies: Map<String, List<String>> = emptyMap(),
        hashMismatches: List<String> = emptyList(),
        fileMissing: List<String> = emptyList(),
        unmanagedPlugins: List<String> = emptyList(),
        outdatedPlugins: List<OutdatedInfo> = emptyList(),
        missingFromLock: List<String> = emptyList(),
        staleLockEntries: List<String> = emptyList(),
        warnings: List<String> = emptyList()
    ) = DoctorReport(
        missingDependencies,
        hashMismatches,
        fileMissing,
        unmanagedPlugins,
        outdatedPlugins,
        missingFromLock,
        staleLockEntries,
        warnings
    )

    @Test
    @DisplayName("empty report has no problems")
    fun emptyHasNoProblems() {
        assertFalse(report().hasProblems)
    }

    @Test
    @DisplayName("outdated and unmanaged alone are informational, not problems")
    fun informationalOnlyIsNotProblem() {
        val r =
            report(
                unmanagedPlugins = listOf("SomePlugin"),
                outdatedPlugins = listOf(OutdatedInfo("Foo", "1.0", "1.1", needsUpdate = true))
            )
        assertFalse(r.hasProblems)
    }

    @Test
    @DisplayName("hash mismatch counts as a problem")
    fun hashMismatchIsProblem() {
        assertTrue(report(hashMismatches = listOf("Foo")).hasProblems)
    }

    @Test
    @DisplayName("lock drift counts as a problem")
    fun lockDriftIsProblem() {
        assertTrue(report(missingFromLock = listOf("Foo")).hasProblems)
    }
}