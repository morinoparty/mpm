/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.config.plugin

import kotlinx.serialization.Serializable
import party.morino.mpm.api.model.plugin.PluginData

@Serializable
data class PluginMetadata(
    val pluginInfo: PluginData,
    val mpmInfo: MpmManagementInfo,
)

@Serializable
data class MpmManagementInfo(
    val repository: RepositoryInfo,
    val version: VersionInfo,
    val download: DownloadInfo,
    val settings: PluginSettings = PluginSettings(),
    val history: List<InstallHistory> = emptyList(),
)

@Serializable
data class VersionInfo(
    val current: LockedVersion,
    val latest: LockedVersion,
    val lastChecked: String,
)

@Serializable
data class InstallHistory(
    val version: String,
    val installedAt: String,
    val action: String,
)
