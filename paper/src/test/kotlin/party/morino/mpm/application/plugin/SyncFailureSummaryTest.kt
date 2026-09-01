/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.application.model.UpdateResult

/**
 * buildSyncFailureMessage（連動更新の失敗要約）の分岐を検証するテスト
 */
@DisplayName("buildSyncFailureMessage - sync child failure summary")
class SyncFailureSummaryTest {
    // 連動更新の結果を組み立てるヘルパー
    private fun result(
        name: String,
        success: Boolean,
        errorMessage: String? = null,
        skipped: Boolean = false
    ) = UpdateResult(
        pluginName = name,
        oldVersion = "1.0.0",
        newVersion = "2.0.0",
        success = success,
        errorMessage = errorMessage,
        skipped = skipped
    )

    @Test
    @DisplayName("Returns null when every sync child succeeded")
    fun returnsNullWhenAllChildrenSucceeded() {
        assertNull(buildSyncFailureMessage(emptyList()))
        assertNull(buildSyncFailureMessage(listOf(result("Child", success = true))))
    }

    @Test
    @DisplayName("Reports the failing sync children")
    fun reportsFailingChildren() {
        val message =
            buildSyncFailureMessage(
                listOf(
                    result("ChildA", success = true),
                    result("ChildB", success = false, errorMessage = "バージョンが存在しません")
                )
            )

        assertNotNull(message)
        // 失敗した子の名前と理由が呼び出し側から見えることが重要
        assertTrue(message!!.contains("ChildB"), "失敗した子の名前が含まれていない: $message")
        assertTrue(message.contains("バージョンが存在しません"), "失敗理由が含まれていない: $message")
    }

    @Test
    @DisplayName("Ignores intentionally skipped sync children")
    fun ignoresSkippedChildren() {
        // ロック中の子は success=false だが skipped=true。意図的な据え置きなので警告を出してはならない
        assertNull(
            buildSyncFailureMessage(
                listOf(
                    result("ChildA", success = true),
                    result(
                        "LockedChild",
                        success = false,
                        errorMessage = "プラグインがロックされています",
                        skipped = true
                    )
                )
            )
        )

        // スキップと本当の失敗が混在する場合は、失敗した子だけが報告される
        val message =
            buildSyncFailureMessage(
                listOf(
                    result(
                        "LockedChild",
                        success = false,
                        errorMessage = "プラグインがロックされています",
                        skipped = true
                    ),
                    result("BrokenChild", success = false, errorMessage = "バージョンが存在しません")
                )
            )

        assertNotNull(message)
        assertTrue(message!!.contains("BrokenChild"), "失敗した子の名前が含まれていない: $message")
        assertFalse(message.contains("LockedChild"), "スキップした子が失敗として報告されている: $message")
    }
}