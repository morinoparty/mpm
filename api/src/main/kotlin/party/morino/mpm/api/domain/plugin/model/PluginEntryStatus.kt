/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
 * and related and neighboring rights to this software to the public domain worldwide.
 * This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.plugin.model

/**
 * 一覧に載る [ManagedPlugin] エントリの由来を表す状態
 *
 * 「mpm.jsonに未登録」と「登録済みだがメタデータを読み込めなかった」を
 * 呼び出し側が区別できるようにするために用いる。
 */
enum class PluginEntryStatus {
    // mpm.jsonに登録され、メタデータも正常に読み込めたプラグイン
    MANAGED,

    // mpm.jsonの管理下にないプラグイン（pluginsディレクトリには存在する）
    UNMANAGED,

    // mpm.jsonには登録されているが、メタデータファイルを読み込めなかったプラグイン
    METADATA_UNAVAILABLE
}