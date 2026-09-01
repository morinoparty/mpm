/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
 * and related and neighboring rights to this software to the public domain worldwide.
 * This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * 連動更新の打ち切り（[SyncFollowBlocklist]）のテスト
 *
 * `A -> B(lock) -> C` で C まで更新してしまう不具合を防ぐための判定を検証する
 */
@DisplayName("SyncFollowBlocklist")
class SyncFollowBlocklistTest {
    @Test
    @DisplayName("Does not block a child whose target updated")
    fun allowsFollowWhenTargetUpdated() {
        val blocklist = SyncFollowBlocklist()

        // 何も記録していなければ追従してよい
        assertNull(blocklist.blockingTargetOf("A"))
        // sync:指定でないプラグインは常に対象外
        assertNull(blocklist.blockingTargetOf(null))
    }

    @Test
    @DisplayName("Blocks descendants transitively through a held node")
    fun blocksDescendantsTransitively() {
        val blocklist = SyncFollowBlocklist()

        // B がロックで据え置かれた
        blocklist.block("B")

        // sync:B の C は追従しない
        assertEquals("B", blocklist.blockingTargetOf("B"))

        // C 自身も追従しなかったので記録し、sync:C の D まで打ち切りが伝播する
        blocklist.block("C")
        assertEquals("C", blocklist.blockingTargetOf("C"))

        // 別系統の親（更新済み）に同期している子は影響を受けない
        assertNull(blocklist.blockingTargetOf("A"))
    }
}