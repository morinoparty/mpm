/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.platform.velocity

import party.morino.mpm.platform.PendingJarShutdownHookFactory
import java.io.File

/**
 * Velocity 用のシャットダウンフック（[VelocityPendingJarShutdownHook]）を作る
 *
 * Velocity にはプラグインの停止を待つ仕組みが無いため、フックは待たずに予約済みのJARを削除する。
 */
class VelocityPendingJarShutdownHookFactory : PendingJarShutdownHookFactory {
    override fun create(
        pluginsDir: File,
        pendingNames: MutableSet<String>
    ): Thread = VelocityPendingJarShutdownHook(pluginsDir, pendingNames)
}