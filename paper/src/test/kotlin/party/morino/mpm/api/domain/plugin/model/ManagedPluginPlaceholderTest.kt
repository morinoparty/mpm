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

package party.morino.mpm.api.domain.plugin.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * メタデータを持たないManagedPluginプレースホルダのテスト
 *
 * 「未登録」と「登録済みだがメタデータ読み込み失敗」を区別できることを検証する
 */
@DisplayName("ManagedPlugin placeholder tests")
class ManagedPluginPlaceholderTest {
    @Test
    @DisplayName("Placeholders expose distinct entry statuses")
    fun placeholdersHaveDistinctStatus() {
        val unmanaged = ManagedPlugin.createUnmanaged("Alpha")
        val metadataUnavailable = ManagedPlugin.createMetadataUnavailable("Beta")

        assertEquals(PluginEntryStatus.UNMANAGED, unmanaged.status)
        assertEquals(PluginEntryStatus.METADATA_UNAVAILABLE, metadataUnavailable.status)
        // 既存の互換性維持: unmanagedのバージョンはセンチネル文字列のまま
        assertEquals("unmanaged", unmanaged.currentVersion.raw)
    }

    @Test
    @DisplayName("Metadata-unavailable placeholder is neither outdated nor locked")
    fun metadataUnavailableIsFilteredOut() {
        val metadataUnavailable = ManagedPlugin.createMetadataUnavailable("Beta")

        // OUTDATED/LOCKEDフィルタから自然に除外されることを保証する
        assertFalse(metadataUnavailable.isOutdated())
        assertFalse(metadataUnavailable.isLocked)
    }
}