/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.application.model.outdated

import kotlinx.serialization.Serializable

/**
 * プラグインの更新情報
 *
 * アプリケーション層で使用される結果DTO
 */
@Serializable
data class OutdatedInfo(
    // プラグイン名
    val pluginName: String,
    // 現在のバージョン
    val currentVersion: String,
    // 上流リポジトリの最新バージョン
    //
    // mpm.json の指定に関わらず「リポジトリが今提供している最新」を表す。
    // 固定バージョン（pin / rollback）のプラグインでも、上流に新しい版があればここに現れる。
    // tag: 指定はそのチャンネルの最新、sync: 指定は同期先の値に補正される。
    val latestVersion: String,
    // 更新が必要かどうか（現在のバージョンと [targetVersion] の正規化済み比較）
    val needsUpdate: Boolean,
    // mpm.json の指定が指す更新先バージョン
    //
    // 固定バージョンならその固定値、latest / tag: なら [latestVersion] と同じ。
    // `mpm update` が実際に揃える先はこちらで、[needsUpdate] もこの値との比較で判定する。
    // 省略時は [latestVersion] と同じ値になる。
    val targetVersion: String = latestVersion,
    // このチェックで latest が前回メタデータに記録された値から変化したか
    //
    // 「新しい上流リリースを検知した瞬間」を表す。更新可能な状態が続いているだけの
    // プラグインは毎回のチェックで false になるため、通知の重複抑制に使える。
    // メタデータを読めなかった場合など、判定できないときは false とする。
    val latestChanged: Boolean = false
) {
    /**
     * 上流の最新が更新先と異なるか（＝固定バージョンが上流に置いていかれているか）
     *
     * raw 文字列の不一致で判定する。latest / tag: 指定では両者は常に一致するため、
     * 実質的に「固定バージョンより新しい版が上流にある」ことを表す。
     * `mpm update` では解消されず、pin の見直し（`mpm pin` / `switch`）が必要な場合の目印になる。
     */
    val hasNewerUpstream: Boolean
        get() = latestVersion != targetVersion
}