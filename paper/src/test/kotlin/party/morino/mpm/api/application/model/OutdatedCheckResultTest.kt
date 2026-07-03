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

package party.morino.mpm.api.application.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.application.model.outdated.OutdatedCheckResult
import party.morino.mpm.api.application.model.outdated.OutdatedInfo
import party.morino.mpm.api.application.model.outdated.PluginCheckError

@DisplayName("OutdatedCheckResult tests")
class OutdatedCheckResultTest {
    @Test
    @DisplayName("Contains both outdated plugins and errors")
    fun testPartialResult() {
        val outdated =
            listOf(
                OutdatedInfo("PluginA", "1.0.0", "2.0.0", true)
            )
        val errors =
            listOf(
                PluginCheckError("PluginB", "Repository not found")
            )

        val result = OutdatedCheckResult(outdated, errors)

        assertEquals(1, result.outdatedPlugins.size)
        assertEquals(1, result.errors.size)
        assertEquals("PluginA", result.outdatedPlugins[0].pluginName)
        assertEquals("PluginB", result.errors[0].pluginName)
    }

    @Test
    @DisplayName("Empty result when all checks succeed with no updates")
    fun testEmptyResult() {
        val result = OutdatedCheckResult(emptyList(), emptyList())

        assertTrue(result.outdatedPlugins.isEmpty())
        assertTrue(result.errors.isEmpty())
    }

    @Test
    @DisplayName("All errors result when no checks succeed")
    fun testAllErrorsResult() {
        val errors =
            listOf(
                PluginCheckError("PluginA", "Network error"),
                PluginCheckError("PluginB", "Metadata not found")
            )

        val result = OutdatedCheckResult(emptyList(), errors)

        assertTrue(result.outdatedPlugins.isEmpty())
        assertEquals(2, result.errors.size)
    }
}