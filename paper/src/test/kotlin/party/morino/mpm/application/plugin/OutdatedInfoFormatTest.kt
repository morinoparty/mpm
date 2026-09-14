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
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.application.model.outdated.OutdatedInfo

/**
 * describeTransition（更新情報の表示整形）のテスト
 */
@DisplayName("describeTransitionのテスト")
class OutdatedInfoFormatTest {
    @Test
    @DisplayName("latest spec shows current to target only")
    fun latestSpecShowsPlainTransition() {
        // latest 指定では target と latest が一致するので注記は付かない
        val info = OutdatedInfo("Plugin", currentVersion = "1.0.0", latestVersion = "1.1.0", needsUpdate = true)

        assertEquals("1.0.0 → 1.1.0", info.describeTransition())
    }

    @Test
    @DisplayName("Pinned plugin shows the upstream latest as a note")
    fun pinnedSpecAppendsUpstreamLatest() {
        // 固定値(1.0.0)に揃っているが上流には 1.2.0 がある。mpm update では届かない版を注記する
        val info =
            OutdatedInfo(
                "Plugin",
                currentVersion = "1.0.0",
                latestVersion = "1.2.0",
                targetVersion = "1.0.0",
                needsUpdate = false
            )

        assertEquals("1.0.0 → 1.0.0 (最新: 1.2.0)", info.describeTransition())
    }
}