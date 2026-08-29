/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.domain.downloader.model.RepositoryType
import party.morino.mpm.api.domain.plugin.dto.ManagedPluginDto
import party.morino.mpm.api.domain.plugin.dto.MetadataDownloadInfoDto
import party.morino.mpm.api.domain.plugin.dto.MpmInfoDto
import party.morino.mpm.api.domain.plugin.dto.PluginInfoDto
import party.morino.mpm.api.domain.plugin.dto.RepositoryInfo
import party.morino.mpm.api.domain.plugin.dto.settings.PluginSettings
import party.morino.mpm.api.domain.plugin.dto.version.VersionDetailDto
import party.morino.mpm.api.domain.plugin.dto.version.VersionManagementDto
import party.morino.mpm.api.domain.plugin.model.ManagedPlugin
import party.morino.mpm.infrastructure.mineauth.model.plugin.PluginSummaryResponse

/**
 * PluginSummaryResponse の変換を検証する
 *
 * 特に、管理外プラグインのセンチネル文字列（"unmanaged" / "unknown"）が
 * そのままクライアントへ漏れないことを確認する。
 */
@DisplayName("PluginSummaryResponse conversion")
class PluginSummaryResponseTest {
    private fun managedPlugin(): ManagedPlugin =
        ManagedPlugin.fromDto(
            ManagedPluginDto(
                pluginInfo =
                    PluginInfoDto(
                        name = "QuickShop",
                        version = "6.1.0",
                        description = "shop",
                        main = null,
                        author = "Ghost",
                        website = null
                    ),
                mpmInfo =
                    MpmInfoDto(
                        repository = RepositoryInfo(type = RepositoryType.MODRINTH, id = "quickshop"),
                        version =
                            VersionManagementDto(
                                current = VersionDetailDto(raw = "6.1.0", normalized = "6.1.0"),
                                latest = VersionDetailDto(raw = "6.2.0", normalized = "6.2.0"),
                                lastChecked = "2026-01-01T00:00:00Z"
                            ),
                        download = MetadataDownloadInfoDto(downloadId = "abc"),
                        settings = PluginSettings(lock = true),
                        history = emptyList()
                    )
            )
        )

    @Test
    @DisplayName("managed plugin exposes versions and repository")
    fun managedPluginIsFullyExposed() {
        val response = PluginSummaryResponse.from(managedPlugin())

        assertEquals("MANAGED", response.status)
        assertTrue(response.isManaged)
        assertEquals("6.1.0", response.currentVersion)
        assertEquals("6.2.0", response.latestVersion)
        assertTrue(response.isOutdated)
        assertTrue(response.isLocked)
        assertEquals("Ghost", response.author)
        assertEquals("MODRINTH", response.repository?.type)
        assertEquals("2026-01-01T00:00:00Z", response.lastChecked)
    }

    @Test
    @DisplayName("unmanaged plugin reports null versions instead of sentinels")
    fun unmanagedSentinelsBecomeNull() {
        val response = PluginSummaryResponse.from(ManagedPlugin.createUnmanaged("SomeJar"))

        assertEquals("UNMANAGED", response.status)
        assertFalse(response.isManaged)
        assertNull(response.currentVersion)
        assertNull(response.latestVersion)
        assertNull(response.repository)
        assertNull(response.lastChecked)
    }

    @Test
    @DisplayName("metadata unavailable plugin reports null versions")
    fun metadataUnavailableSentinelsBecomeNull() {
        val response = PluginSummaryResponse.from(ManagedPlugin.createMetadataUnavailable("Broken"))

        assertEquals("METADATA_UNAVAILABLE", response.status)
        assertFalse(response.isManaged)
        assertNull(response.currentVersion)
        assertNull(response.latestVersion)
    }
}