/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm

import io.papermc.paper.plugin.bootstrap.BootstrapContext
import io.papermc.paper.plugin.bootstrap.PluginBootstrap
import io.papermc.paper.plugin.bootstrap.PluginProviderContext
import org.bukkit.plugin.java.JavaPlugin

/**
 * MinecraftPluginManagerのブートストラップクラス
 * プラグインの初期化処理を行う
 *
 * Lampコマンドフレームワークを使用してコマンドシステムを構築する
 * - ParameterTypeでカスタムパラメータ型を処理
 * - Brigadier統合により1.13+のコマンド補完をサポート
 * - Koin DIと統合してビジネスロジックを注入
 */
@Suppress("unused")
class MpmBootstrap : PluginBootstrap {
    /**
     * プラグインのブートストラップ処理を行うメソッド
     * @param context ブートストラップコンテキスト
     */
    override fun bootstrap(context: BootstrapContext) {
        // Lampコマンドハンドラーの初期化はMpm.onEnable()で行う
    }

    /**
     * プラグインのインスタンスを作成するメソッド
     * @param context プラグインプロバイダコンテキスト
     * @return mpmプラグインのインスタンス
     */
    override fun createPlugin(context: PluginProviderContext): JavaPlugin = Mpm()
}