/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth

import org.bukkit.plugin.java.JavaPlugin
import party.morino.mineauth.api.MineAuthApi
import party.morino.mineauth.api.http.HttpStatus

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
     * 登録の直前に [httpError] を1度呼んでエラー応答のリンクを検証し、
     * 失敗する場合は登録せずに打ち切る（中途半端に動く API を公開しないため）。
     */
    fun setup() {
        // NoClassDefFoundError など Throwable も含めて全体を保護する。
        // MineAuth 未導入時は compileOnly の API クラス（EndpointRegistrationException 等）が
        // 解決できず NoClassDefFoundError が発生しうる。
        //
        // 重要: MineAuth 由来の型は「本体で遅延解決される」参照だけに留め、catch 節の例外型には
        // 使わないこと。catch 節の例外型はメソッド検証時（＝呼び出した瞬間）に即ロードされるため、
        // MineAuth が無い環境では setup() の呼び出し自体が catch(Throwable) の外側で
        // NoClassDefFoundError を投げ、soft dependency の保護をすり抜けてしまう。
        // そのため EndpointRegistrationException は個別 catch せず catch(Throwable) でまとめて扱う。
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

            // エラー応答のリンク検証（fail-fast）。
            // mineauth-api にはバージョンを示す定数が存在しないため「コンパイル時のリビジョンと
            // 実行時のリビジョンを突き合わせる」検査は原理的に書けない。
            // 代わりに、エラー応答の要である HttpError を1度だけ実際に生成してみる。
            // これはリンクの解決を強制するため、クラスローダー不整合（LinkageError）も
            // 本当の API 変更（NoSuchMethodError も LinkageError のサブクラス）も同時に捕捉できる。
            // 失敗したまま登録すると「成功時だけ動き、失敗時はすべて汎用 500」という
            // 中途半端な API を公開してしまうため、登録せずに打ち切る。
            try {
                httpError(HttpStatus.INTERNAL_SERVER_ERROR, "mpm MineAuth linkage probe")
            } catch (e: LinkageError) {
                // catch 節の例外型に MineAuth 由来の型を使えない制約は上記コメントのとおり。
                // LinkageError は JDK の型なので安全に catch できる。
                plugin.logger.severe(
                    "MineAuth integration failed: HttpError could not be constructed " +
                        "(${e::class.simpleName}: ${e.message}) - HTTP API will be unavailable. " +
                        "mpm's shaded Kotlin stdlib may clash with the one MineAuth loads via 'libraries:'"
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
        } catch (e: Throwable) {
            // 登録検証エラー（EndpointRegistrationException。message に全検証エラーを含む）や
            // NoClassDefFoundError（互換性のない MineAuth など）を含む全エラーをキャッチする。
            // EndpointRegistrationException を個別 catch しないのは上記コメントの理由による。
            plugin.logger.warning(
                "MineAuth integration failed (${e::class.simpleName}): ${e.message} - HTTP API will be unavailable"
            )
        }
    }
}