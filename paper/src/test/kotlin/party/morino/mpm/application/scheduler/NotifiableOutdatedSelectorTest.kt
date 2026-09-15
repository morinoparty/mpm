/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.scheduler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.application.model.outdated.OutdatedInfo

@DisplayName("NotifiableOutdatedSelector - which outdated plugins are worth a webhook")
class NotifiableOutdatedSelectorTest {
    private fun info(
        name: String,
        latestVersion: String = "2.0.0"
    ) = OutdatedInfo(
        pluginName = name,
        currentVersion = "1.0.0",
        latestVersion = latestVersion,
        needsUpdate = true
    )

    private fun classification(
        autoUpdate: List<OutdatedInfo> = emptyList(),
        syncFollower: List<OutdatedInfo> = emptyList(),
        lockedSync: List<OutdatedInfo> = emptyList(),
        checkOnly: List<OutdatedInfo> = emptyList(),
        locked: List<OutdatedInfo> = emptyList(),
        unknown: List<OutdatedInfo> = emptyList()
    ) = UpdateCandidateClassification(
        autoUpdate = autoUpdate,
        syncFollower = syncFollower,
        lockedSync = lockedSync,
        checkOnly = checkOnly,
        locked = locked,
        unknown = unknown
    )

    @Test
    @DisplayName("a successful auto-update is not announced")
    fun testSuccessfulAutoUpdateIsSilent() {
        // 直後に PluginUpdateEvent が飛ぶので、OUTDATED を出すと完全な重複になる
        val result =
            NotifiableOutdatedSelector.select(
                classification(autoUpdate = listOf(info("LuckPerms"))),
                failedAutoUpdates = emptySet(),
                notifiedLatest = emptyMap()
            )
        assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("a failed auto-update is announced")
    fun testFailedAutoUpdateIsAnnounced() {
        val result =
            NotifiableOutdatedSelector.select(
                classification(autoUpdate = listOf(info("LuckPerms"))),
                failedAutoUpdates = setOf("LuckPerms"),
                notifiedLatest = emptyMap()
            )
        assertEquals(listOf("LuckPerms"), result.map { it.pluginName })
    }

    @Test
    @DisplayName("sync followers are left to their parent")
    fun testSyncFollowerIsSilent() {
        val result =
            NotifiableOutdatedSelector.select(
                classification(syncFollower = listOf(info("MineAuth-addon-vault"))),
                failedAutoUpdates = emptySet(),
                notifiedLatest = emptyMap()
            )
        assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("plugins nobody but a human can move are announced")
    fun testManualOnlyClassificationsAreAnnounced() {
        val result =
            NotifiableOutdatedSelector.select(
                classification(
                    checkOnly = listOf(info("Vault")),
                    locked = listOf(info("WorldEdit")),
                    lockedSync = listOf(info("PAPIProxyBridge")),
                    unknown = listOf(info("Broken"))
                ),
                failedAutoUpdates = emptySet(),
                notifiedLatest = emptyMap()
            )
        assertEquals(
            listOf("Vault", "WorldEdit", "PAPIProxyBridge", "Broken").sorted(),
            result.map { it.pluginName }.sorted()
        )
    }

    @Test
    @DisplayName("an already announced latest version is not announced again")
    fun testAlreadyAnnouncedLatestIsNotRepeated() {
        // 更新可能な状態が続いているだけのプラグインを毎日鳴らさない
        val result =
            NotifiableOutdatedSelector.select(
                classification(locked = listOf(info("WorldEdit", latestVersion = "2.0.0"))),
                failedAutoUpdates = emptySet(),
                notifiedLatest = mapOf("WorldEdit" to "2.0.0")
            )
        assertTrue(result.isEmpty())
    }

    @Test
    @DisplayName("a newer upstream release is announced even if an older one was")
    fun testNewerLatestIsAnnouncedAgain() {
        // 台帳にあるのは 2.0.0 の通知。上流が 3.0.0 を出したらもう一度鳴らす
        val result =
            NotifiableOutdatedSelector.select(
                classification(checkOnly = listOf(info("Vault", latestVersion = "3.0.0"))),
                failedAutoUpdates = emptySet(),
                notifiedLatest = mapOf("Vault" to "2.0.0")
            )
        assertEquals(listOf("Vault"), result.map { it.pluginName })
    }
}