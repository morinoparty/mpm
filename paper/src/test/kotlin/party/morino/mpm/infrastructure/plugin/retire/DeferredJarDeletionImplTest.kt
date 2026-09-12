/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.plugin.retire

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DeferredJarDeletionImplTest {
    @Test
    @DisplayName("deletePending removes existing files and drops missing ones")
    fun testDeletePendingRemovesAndDropsMissing(
        @TempDir pluginsDir: File
    ) {
        val old = File(pluginsDir, "mpm_0.0.25.jar").apply { writeText("old") }
        val keep = File(pluginsDir, "mpm_0.0.26.jar").apply { writeText("new") }

        val (deleted, remaining) =
            DeferredJarDeletionImpl.deletePending(listOf("mpm_0.0.25.jar", "already-gone.jar"), pluginsDir)

        assertEquals(listOf(old), deleted)
        assertTrue(remaining.isEmpty())
        assertFalse(old.exists())
        assertTrue(keep.exists())
    }
}