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
import party.morino.mpm.platform.SelfInfo
import party.morino.mpm.utils.runningJarOf
import java.io.File

/**
 * Paper 上で動作している mpm 自身の情報
 *
 * プラグイン名・バージョンは plugin.yml / paper-plugin.yml の値を、JARはメインクラスの読み込み元を返す。
 */
class PaperSelfInfo :
    SelfInfo,
    KoinComponent {
    // Koinによる依存性注入
    private val plugin: JavaPlugin by inject()

    override val id: String
        get() = plugin.name

    override val version: String
        get() = plugin.pluginMeta.version

    override val jar: File?
        get() = runningJarOf(plugin)
}