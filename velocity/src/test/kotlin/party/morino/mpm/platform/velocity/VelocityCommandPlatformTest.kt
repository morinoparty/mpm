/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.platform.velocity

import com.velocitypowered.api.permission.PermissionSubject
import com.velocitypowered.api.permission.Tristate
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

class VelocityCommandPlatformTest {
    // 指定したノードにだけ値を持つ対象を作る（それ以外は未設定）
    private fun subjectWith(vararg values: Pair<String, Tristate>): PermissionSubject {
        val map = values.toMap()
        return mockk { every { getPermissionValue(any()) } answers { map[firstArg()] ?: Tristate.UNDEFINED } }
    }

    @Test
    @DisplayName("parent node grants child command permissions")
    fun parentGrantsChild() {
        val subject = subjectWith("mpm.command" to Tristate.TRUE)
        assertTrue(VelocityCommandPlatform.hasPermission(subject, "mpm.command.add"))
        assertTrue(VelocityCommandPlatform.hasPermission(subject, "mpm.command"))
    }

    @Test
    @DisplayName("explicit false on child overrides granted parent")
    fun explicitFalseWins() {
        val subject = subjectWith("mpm.command" to Tristate.TRUE, "mpm.command.add" to Tristate.FALSE)
        assertFalse(VelocityCommandPlatform.hasPermission(subject, "mpm.command.add"))
    }

    @Test
    @DisplayName("denies when nothing is set and ignores the bare mpm node")
    fun undefinedIsDenied() {
        assertFalse(VelocityCommandPlatform.hasPermission(subjectWith(), "mpm.command.list"))
        assertFalse(VelocityCommandPlatform.hasPermission(subjectWith("mpm" to Tristate.TRUE), "mpm.command.list"))
    }
}