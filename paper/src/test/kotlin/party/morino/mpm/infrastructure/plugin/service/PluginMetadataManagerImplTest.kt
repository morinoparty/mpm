/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.plugin.service

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import party.morino.mpm.MpmTest
import party.morino.mpm.api.domain.downloader.model.VersionData
import party.morino.mpm.api.domain.repository.RepositoryConfig

/**
 * PluginMetadataManagerImplのテスト
 * 主にパストラバーサル防止（issue #284）を検証する
 */
@ExtendWith(MpmTest::class)
class PluginMetadataManagerImplTest {
    // KoinComponentのためGlobalContext経由でpluginDirectoryが注入される
    private val manager = PluginMetadataManagerImpl()

    /**
     * 有効なメタデータDTOを生成するヘルパー
     */
    private fun validMetadata() =
        runBlocking {
            manager
                .createMetadata(
                    pluginName = "ValidPlugin",
                    repository = RepositoryConfig(type = "modrinth", repositoryId = "test"),
                    versionData = VersionData(downloadId = "dl", version = "1.0.0"),
                    action = "install",
                    channel = null
                ).getOrNull()!!
        }

    @Test
    @DisplayName("createMetadata should reject path traversal plugin name")
    fun createRejectsTraversal() {
        runBlocking {
            val result =
                manager.createMetadata(
                    pluginName = "../../config",
                    repository = RepositoryConfig(type = "modrinth", repositoryId = "test"),
                    versionData = VersionData(downloadId = "dl", version = "1.0.0"),
                    action = "install",
                    channel = null
                )
            assertTrue(result.isLeft())
            assertTrue(result.leftOrNull()?.contains("不正な") == true)
        }
    }

    @Test
    @DisplayName("saveMetadata should reject path traversal plugin name")
    fun saveRejectsTraversal() {
        val result = manager.saveMetadata("../../evil", validMetadata())
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.contains("不正な") == true)
    }

    @Test
    @DisplayName("loadMetadata should reject backslash path separators")
    fun loadRejectsBackslash() {
        val result = manager.loadMetadata("..\\evil")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.contains("不正な") == true)
    }

    @Test
    @DisplayName("deleteMetadata should reject path traversal plugin name")
    fun deleteRejectsTraversal() {
        val result = manager.deleteMetadata("../../evil")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.contains("不正な") == true)
    }

    @Test
    @DisplayName("loadMetadata should reject Windows drive-qualified name")
    fun loadRejectsDriveQualifiedName() {
        // "C:evil" のようなドライブ修飾名も拒否される
        val result = manager.loadMetadata("C:evil")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.contains("不正な") == true)
    }

    @Test
    @DisplayName("loadMetadata should accept valid name and pass sanitization")
    fun loadAcceptsValidName() {
        // 存在しないが正当な名前: サニタイズは通過し「見つかりません」エラーになる
        val result = manager.loadMetadata("NonExistentPlugin")
        assertTrue(result.isLeft())
        assertTrue(result.leftOrNull()?.contains("見つかりません") == true)
    }
}