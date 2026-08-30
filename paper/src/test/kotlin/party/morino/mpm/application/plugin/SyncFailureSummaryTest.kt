/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

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
        errorMessage: String? = null
    ) = UpdateResult(
        pluginName = name,
        oldVersion = "1.0.0",
        newVersion = "2.0.0",
        success = success,
        errorMessage = errorMessage
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
}