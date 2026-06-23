/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth

import org.bukkit.plugin.java.JavaPlugin
import party.morino.mineauth.api.MineAuthAPI

/**
 * MineAuth との連携を管理するクラス
 *
 * MineAuth プラグインが存在する場合のみ HTTP エンドポイントを登録する。
 * 存在しない場合は何もしない（soft dependency として扱う）。
 */
class MineAuthIntegration(
    private val plugin: JavaPlugin
) {
    /**
     * MineAuth が利用可能か確認してエンドポイントを登録する
     *
     * MineAuth が存在しない場合は警告を出さずにスキップする。
     */
    fun setup() {
        // NoClassDefFoundError など Throwable も含めて全体を保護する。
        // 古いバージョンや互換性のない MineAuth が存在する場合に as? キャストで
        // クラスロードが失敗する可能性があるため、try-catch は最外側に配置する。
        try {
            val mineAuthPlugin = plugin.server.pluginManager.getPlugin("MineAuth")
            if (mineAuthPlugin == null) {
                plugin.logger.info("MineAuth not found - HTTP API integration disabled")
                return
            }

            // 互換性のない MineAuth の場合 ClassCastException / NoClassDefFoundError が発生しうる
            val mineAuthApi = mineAuthPlugin as? MineAuthAPI
            if (mineAuthApi == null) {
                plugin.logger.warning(
                    "MineAuth found but doesn't implement MineAuthAPI - HTTP API integration disabled"
                )
                return
            }

            // mpm のエンドポイントを MineAuth に登録する
            // 登録後は /api/v1/plugins/mpm/ 配下でアクセス可能になる
            mineAuthApi
                .createHandler(plugin)
                .register(MpmPluginHandler())
            plugin.logger.info("MineAuth integration enabled - endpoints registered at /api/v1/plugins/mpm/")
        } catch (e: Throwable) {
            // NoClassDefFoundError、ClassCastException など を含む全エラーをキャッチ
            plugin.logger.warning(
                "MineAuth integration failed (${e::class.simpleName}): ${e.message} - HTTP API will be unavailable"
            )
        }
    }
}