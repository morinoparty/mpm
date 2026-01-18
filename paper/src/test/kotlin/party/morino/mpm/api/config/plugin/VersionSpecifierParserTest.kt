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

package party.morino.mpm.api.config.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("VersionSpecifierParser tests")
class VersionSpecifierParserTest {
    @Nested
    @DisplayName("parse() tests")
    inner class ParseTest {
        @Test
        @DisplayName("Parses 'latest' to Latest")
        fun testParseLatest() {
            val result = VersionSpecifierParser.parse("latest")
            assertEquals(VersionSpecifier.Latest, result)
        }

        @Test
        @DisplayName("Parses 'LATEST' (case-insensitive) to Latest")
        fun testParseLatestCaseInsensitive() {
            val result = VersionSpecifierParser.parse("LATEST")
            assertEquals(VersionSpecifier.Latest, result)
        }

        @Test
        @DisplayName("Parses 'sync:PluginName' to Sync")
        fun testParseSync() {
            val result = VersionSpecifierParser.parse("sync:QuickShop")
            assertTrue(result is VersionSpecifier.Sync)
            assertEquals("QuickShop", (result as VersionSpecifier.Sync).targetPlugin)
        }

        @Test
        @DisplayName("Parses 'SYNC:PluginName' (case-insensitive) to Sync")
        fun testParseSyncCaseInsensitive() {
            val result = VersionSpecifierParser.parse("SYNC:QuickShop")
            assertTrue(result is VersionSpecifier.Sync)
            assertEquals("QuickShop", (result as VersionSpecifier.Sync).targetPlugin)
        }

        @Test
        @DisplayName("Parses 'tag:stable' to Tag")
        fun testParseTag() {
            val result = VersionSpecifierParser.parse("tag:stable")
            assertTrue(result is VersionSpecifier.Tag)
            assertEquals("stable", (result as VersionSpecifier.Tag).tag)
        }

        @Test
        @DisplayName("Parses 'pattern:^5\\.4\\..*' to Pattern")
        fun testParsePattern() {
            val result = VersionSpecifierParser.parse("pattern:^5\\.4\\..*")
            assertTrue(result is VersionSpecifier.Pattern)
            assertEquals("^5\\.4\\..*", (result as VersionSpecifier.Pattern).pattern)
        }

        @Test
        @DisplayName("Parses version string to Fixed")
        fun testParseFixed() {
            val result = VersionSpecifierParser.parse("1.2.3")
            assertTrue(result is VersionSpecifier.Fixed)
            assertEquals("1.2.3", (result as VersionSpecifier.Fixed).version)
        }

        @Test
        @DisplayName("Parses complex version string to Fixed")
        fun testParseComplexFixed() {
            val result = VersionSpecifierParser.parse("6.2.0-SNAPSHOT")
            assertTrue(result is VersionSpecifier.Fixed)
            assertEquals("6.2.0-SNAPSHOT", (result as VersionSpecifier.Fixed).version)
        }
    }

    @Nested
    @DisplayName("toVersionString() tests")
    inner class ToVersionStringTest {
        @Test
        @DisplayName("Converts Latest to 'latest'")
        fun testLatestToString() {
            val result = VersionSpecifierParser.toVersionString(VersionSpecifier.Latest)
            assertEquals("latest", result)
        }

        @Test
        @DisplayName("Converts Sync to 'sync:PluginName'")
        fun testSyncToString() {
            val result = VersionSpecifierParser.toVersionString(VersionSpecifier.Sync("QuickShop"))
            assertEquals("sync:QuickShop", result)
        }

        @Test
        @DisplayName("Converts Tag to 'tag:tagname'")
        fun testTagToString() {
            val result = VersionSpecifierParser.toVersionString(VersionSpecifier.Tag("stable"))
            assertEquals("tag:stable", result)
        }

        @Test
        @DisplayName("Converts Pattern to 'pattern:pattern'")
        fun testPatternToString() {
            val result = VersionSpecifierParser.toVersionString(VersionSpecifier.Pattern("^5\\.4\\..*"))
            assertEquals("pattern:^5\\.4\\..*", result)
        }

        @Test
        @DisplayName("Converts Fixed to version string")
        fun testFixedToString() {
            val result = VersionSpecifierParser.toVersionString(VersionSpecifier.Fixed("1.2.3"))
            assertEquals("1.2.3", result)
        }
    }

    @Nested
    @DisplayName("isSyncFormat() tests")
    inner class IsSyncFormatTest {
        @Test
        @DisplayName("Returns true for sync format")
        fun testIsSyncFormat() {
            assertTrue(VersionSpecifierParser.isSyncFormat("sync:QuickShop"))
        }

        @Test
        @DisplayName("Returns true for SYNC format (case-insensitive)")
        fun testIsSyncFormatCaseInsensitive() {
            assertTrue(VersionSpecifierParser.isSyncFormat("SYNC:QuickShop"))
        }

        @Test
        @DisplayName("Returns false for non-sync format")
        fun testIsNotSyncFormat() {
            assertFalse(VersionSpecifierParser.isSyncFormat("1.2.3"))
            assertFalse(VersionSpecifierParser.isSyncFormat("latest"))
            assertFalse(VersionSpecifierParser.isSyncFormat("tag:stable"))
        }
    }

    @Nested
    @DisplayName("extractSyncTarget() tests")
    inner class ExtractSyncTargetTest {
        @Test
        @DisplayName("Extracts target from sync format")
        fun testExtractSyncTarget() {
            val result = VersionSpecifierParser.extractSyncTarget("sync:QuickShop")
            assertEquals("QuickShop", result)
        }

        @Test
        @DisplayName("Extracts target from SYNC format (case-insensitive)")
        fun testExtractSyncTargetCaseInsensitive() {
            val result = VersionSpecifierParser.extractSyncTarget("SYNC:QuickShop")
            assertEquals("QuickShop", result)
        }

        @Test
        @DisplayName("Returns null for non-sync format")
        fun testExtractSyncTargetNonSync() {
            assertNull(VersionSpecifierParser.extractSyncTarget("1.2.3"))
            assertNull(VersionSpecifierParser.extractSyncTarget("latest"))
        }

        @Test
        @DisplayName("Handles plugin name with special characters")
        fun testExtractSyncTargetSpecialChars() {
            val result = VersionSpecifierParser.extractSyncTarget("sync:QuickShop-Hikari-Addon")
            assertEquals("QuickShop-Hikari-Addon", result)
        }
    }
}
