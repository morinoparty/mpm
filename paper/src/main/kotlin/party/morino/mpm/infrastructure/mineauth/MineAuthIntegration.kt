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
import party.morino.mineauth.api.EndpointRegistrationException
import party.morino.mineauth.api.MineAuthApi

/**
 * MineAuth との連携を管理するクラス
 *
 * MineAuth プラグインが存在する場合のみ HTTP エンドポイントを登録する。
 * 存在しない場合は何もしない（soft dependency として扱う）。
 *
 * MineAuth v2 API（[MineAuthApi]）に対応する。
 * API インスタンスは Bukkit の ServicesManager 経由で取得し、
 * エンドポイントは all-or-nothing で登録される（1つでも検証に失敗すれば何もマウントされない）。
 */
class MineAuthIntegration(
    private val plugin: JavaPlugin
) {
    /**
     * MineAuth が利用可能か確認してエンドポイントを登録する
     *
     * MineAuth が存在しない場合は情報ログのみ出力してスキップする。
     */
    fun setup() {
        // NoClassDefFoundError など Throwable も含めて全体を保護する。
        // MineAuth 未導入時は compileOnly の API クラスが解決できず
        // NoClassDefFoundError が発生しうるため、try-catch は最外側に配置する。
        try {
            // MineAuth プラグインの存在確認。
            // API クラスに触れずに soft dependency を判定することで、
            // 未導入時に NoClassDefFoundError を発生させず info ログで済ませる。
            if (plugin.server.pluginManager.getPlugin("MineAuth") == null) {
                plugin.logger.info("MineAuth not found - HTTP API integration disabled")
                return
            }

            // ServicesManager 経由で MineAuthApi を取得する（v2 API のエントリーポイント）
            val api = MineAuthApi.get(plugin.server)
            if (api == null) {
                plugin.logger.warning(
                    "MineAuth found but MineAuthApi service is unavailable - HTTP API integration disabled"
                )
                return
            }

            // mpm 名前空間でハンドラーを登録する（/api/v1/plugins/mpm/ 配下にマウントされる）。
            // 登録は all-or-nothing で、失敗時は EndpointRegistrationException がスローされる。
            // プラグイン無効化時には MineAuth 側で自動的に登録解除される。
            val registration = api.register(plugin, "mpm", MpmPluginHandler())
            plugin.logger.info(
                "MineAuth integration enabled - " +
                    "${registration.endpoints.size} endpoints registered under ${registration.basePath}/"
            )
        } catch (e: EndpointRegistrationException) {
            // 登録は all-or-nothing のため、検証エラーの全リストをまとめて出力する
            plugin.logger.warning(
                "MineAuth endpoint registration failed - HTTP API will be unavailable:\n${e.message}"
            )
        } catch (e: Throwable) {
            // NoClassDefFoundError（互換性のない MineAuth など）を含む全エラーをキャッチ
            plugin.logger.warning(
                "MineAuth integration failed (${e::class.simpleName}): ${e.message} - HTTP API will be unavailable"
            )
        }
    }
}