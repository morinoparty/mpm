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
import party.morino.mpm.infrastructure.plugin.retire.PendingJarShutdownHook
import party.morino.mpm.platform.PendingJarShutdownHookFactory
import java.io.File

/**
 * Paper 用のシャットダウンフック（[PendingJarShutdownHook]）を作る
 *
 * フックは予約済みのJARを読み込んでいるプラグインがすべて無効化されるまで待ってから削除する。
 */
class PaperPendingJarShutdownHookFactory :
    PendingJarShutdownHookFactory,
    KoinComponent {
    // Koinによる依存性注入
    private val plugin: JavaPlugin by inject()

    override fun create(
        pluginsDir: File,
        pendingNames: MutableSet<String>
    ): Thread = PendingJarShutdownHook(plugin.server, pluginsDir, pendingNames)
}