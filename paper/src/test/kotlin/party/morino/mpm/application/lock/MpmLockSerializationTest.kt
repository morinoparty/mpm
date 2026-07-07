/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.lock

import com.charleskorn.kaml.Yaml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.domain.downloader.model.RepositoryType
import party.morino.mpm.api.domain.plugin.dto.MetadataDownloadInfoDto
import party.morino.mpm.api.domain.plugin.dto.RepositoryInfo
import party.morino.mpm.api.domain.plugin.dto.version.VersionDetailDto
import party.morino.mpm.api.domain.project.lock.LockEntry
import party.morino.mpm.api.domain.project.lock.MpmLock

/**
 * mpm-lock.yaml（MpmLock）のYAMLシリアライズが正しく往復することを検証する
 */
@DisplayName("MpmLock - YAML serialization")
class MpmLockSerializationTest {
    private fun sampleLock(): MpmLock =
        MpmLock(
            lockfileVersion = MpmLock.CURRENT_LOCKFILE_VERSION,
            generatedAt = "2025-01-15T10:30:00Z",
            plugins =
                mapOf(
                    "LuckPerms" to
                        LockEntry(
                            version = VersionDetailDto(raw = "v5.4.102", normalized = "5.4.102"),
                            download =
                                MetadataDownloadInfoDto(
                                    downloadId = "v5.4.102",
                                    fileName = "LuckPerms-Bukkit-5.4.102.jar",
                                    url = "https://example.com/LuckPerms.jar",
                                    sha256 = "abc123"
                                ),
                            repository = RepositoryInfo(type = RepositoryType.GITHUB, id = "LuckPerms/LuckPerms"),
                            installedAt = "2025-01-15T10:30:00Z"
                        )
                )
        )

    @Test
    @DisplayName("round-trips through YAML preserving all fields")
    fun roundTrips() {
        val lock = sampleLock()
        val yaml = Yaml.default.encodeToString(MpmLock.serializer(), lock)
        val parsed = Yaml.default.decodeFromString(MpmLock.serializer(), yaml)

        assertEquals(lock, parsed)
        // 主要フィールドがYAMLに含まれること
        assertTrue(yaml.contains("lockfileVersion"))
        assertTrue(yaml.contains("LuckPerms"))
        assertTrue(yaml.contains("sha256"))
    }
}