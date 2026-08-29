/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth

/**
 * MineAuth HTTP API のパーミッションノード
 *
 * 読み取り専用の操作と、サーバー状態を変更する操作を別々のノードに分割している。
 * 従来の `mpm.api` は両方を子に持つ親パーミッションとして [party.morino.mpm.Mpm] に
 * 登録されるため、既存の `mpm.api` 付与はそのまま全エンドポイントへのアクセスを維持する。
 *
 * 適用範囲（MineAuth の設計による）:
 * `@Authenticated(permission = ...)` はユーザープリンシパルに対して評価される。
 * サービストークンは管理者が発行する信頼された資格情報として権限の評価対象外であり、
 * エンドポイントごとの `callers` によってアクセスの可否が決まる。
 * したがって、この読み書き分割はユーザートークンに対して働く。
 */
object MpmApiPermission {
    /** 親パーミッション（後方互換のため維持する。read/write の両方を含む） */
    const val ROOT = "mpm.api"

    /** 読み取り専用エンドポイント用のパーミッション */
    const val READ = "mpm.api.read"

    /** サーバー状態を変更するエンドポイント用のパーミッション */
    const val WRITE = "mpm.api.write"
}