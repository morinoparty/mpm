/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.domain.downloader.model.VersionData

@DisplayName("JarVersionMatcher - match a JAR version to a repository version")
class JarVersionMatcherTest {
    // GitHub のように "v" 付きのタグで配布しているリポジトリを想定
    private val versions =
        listOf(
            VersionData(downloadId = "3", version = "v0.0.27"),
            VersionData(downloadId = "2", version = "v0.0.26"),
            VersionData(downloadId = "1", version = "0.0.25")
        )

    @Test
    @DisplayName("Matches a tag that only differs by the v prefix")
    fun matchesVPrefixedTag() {
        assertEquals("2", JarVersionMatcher.findMatchingVersion("0.0.26", versions, null)?.downloadId)
        assertEquals("1", JarVersionMatcher.findMatchingVersion("v0.0.25", versions, null)?.downloadId)
    }

    @Test
    @DisplayName("Returns null when the version is not in the repository")
    fun returnsNullWhenMissing() {
        assertNull(JarVersionMatcher.findMatchingVersion("0.0.99", versions, null))
    }
}