/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.platform.paper

import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.platform.LoadedPluginRegistry
import party.morino.mpm.utils.runningJarOf
import java.io.File

/**
 * Paper の PluginManager から、読み込まれているプラグインのJARを取得する
 */
class PaperLoadedPluginRegistry :
    LoadedPluginRegistry,
    KoinComponent {
    // Koinによる依存性注入
    private val plugin: JavaPlugin by inject()

    override fun loadedJars(): List<File> =
        // 無効化済みのプラグインもクラスローダーがJARを参照し続けるため、すべてのプラグインを対象にする
        plugin.server.pluginManager.plugins
            .mapNotNull { runningJarOf(it) }

    override fun enabledJars(): List<File> =
        // 無効化済みのプラグインはJARを使い終えているため除く
        plugin.server.pluginManager.plugins
            .filter { it.isEnabled }
            .mapNotNull { runningJarOf(it) }
}