/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.mock.config

import java.io.File
import party.morino.mpm.api.domain.config.PluginDirectory

class PluginDirectoryMock : PluginDirectory {
    private val rootDirectory: File = File("src/test/resources/plugins/mpm")

    override fun getRootDirectory(): File = rootDirectory

    override fun getPluginsDirectory(): File = rootDirectory.parentFile

    override fun getMetadataDirectory(): File = rootDirectory.resolve("metadata")

    override fun getRepositoryDirectory(): File = rootDirectory.resolve("repository")

    override fun getBackupsDirectory(): File = rootDirectory.resolve("backups")
}