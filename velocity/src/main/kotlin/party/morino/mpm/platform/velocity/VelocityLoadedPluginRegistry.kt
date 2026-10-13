/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.platform.velocity

import com.velocitypowered.api.proxy.ProxyServer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.platform.LoadedPluginRegistry
import java.io.File

/**
 * Velocity の PluginManager から、読み込まれているプラグインのJARを取得する
 *
 * Velocity にはプラグインの無効化・アンロードが無く、読み込んだプラグインはプロキシの停止まで動き続ける。
 */
class VelocityLoadedPluginRegistry :
    LoadedPluginRegistry,
    KoinComponent {
    // Koinによる依存性注入
    private val proxy: ProxyServer by inject()

    override fun loadedJars(): List<File> =
        // Velocity 本体を表す仮想プラグイン（velocity）は読み込み元のJARを持たないため除く
        proxy.pluginManager.plugins
            .mapNotNull {
                it.description.source
                    .orElse(null)
                    ?.toFile()
            }

    /**
     * 停止時にまだJARを使っているプラグインとして、常に空を返す
     *
     * この値は ProxyShutdownEvent で削除予約済みのJARを消すとき（DeferredJarDeletion.deleteScheduled）にだけ使われる。
     * Velocity はプラグインのクラスローダーを閉じず、JARは JVM の終了まで開いたままになる。
     * Linux / macOS では開いているJARのファイルを削除しても、開いているハンドルから残りのクラスを読めるため、
     * ProxyShutdownEvent の時点で予約済みのJARをすべて削除してよい。
     * 次回起動時に同じIDのJARが2つ並ぶと片方が読み込まれないため、ここで消しておく必要がある。
     * Windows など開いているJARを削除できない環境では削除に失敗し、予約に残ったものを JVM 終了時のフックと次回起動時の片付けが削除する。
     *
     * @return 常に空のリスト
     */
    override fun enabledJars(): List<File> = emptyList()
}