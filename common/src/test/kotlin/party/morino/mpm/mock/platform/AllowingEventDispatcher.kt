/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.mock.platform

import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import party.morino.mpm.platform.MpmEventDispatcher
import java.io.File

/**
 * テスト用の [MpmEventDispatcher]
 *
 * リスナーが存在しない状態を模し、キャンセル可能なイベントはすべて許可する。
 */
class AllowingEventDispatcher : MpmEventDispatcher {
    override suspend fun firePluginAdd(
        repositoryPlugin: RepositoryPlugin,
        versionSpecifier: VersionSpecifier,
        repositoryType: String,
        repositoryId: String
    ): Boolean = true

    override suspend fun firePluginInstall(
        repositoryPlugin: RepositoryPlugin,
        version: String,
        repositoryType: String,
        repositoryId: String
    ): Boolean = true

    override suspend fun firePluginRemove(installedPlugin: InstalledPlugin): Boolean = true

    override suspend fun firePluginUninstall(
        installedPlugin: InstalledPlugin,
        jarFile: File?
    ): Boolean = true

    override suspend fun firePluginUpdate(
        installedPlugin: InstalledPlugin,
        beforeVersion: VersionSpecifier,
        targetVersion: VersionSpecifier
    ): Boolean = true

    override suspend fun firePluginLock(
        installedPlugin: InstalledPlugin,
        currentVersion: String
    ): Boolean = true

    override suspend fun firePluginUnlock(
        installedPlugin: InstalledPlugin,
        currentVersion: String
    ): Boolean = true

    override suspend fun firePluginJarDelete(
        fileName: String,
        jarFile: File,
        deferred: Boolean
    ) {
        // テストではリスナーが無いため何もしない
    }

    override suspend fun firePluginOutdated(
        installedPlugin: InstalledPlugin,
        currentVersion: String,
        latestVersion: String
    ) {
        // テストではリスナーが無いため何もしない
    }
}