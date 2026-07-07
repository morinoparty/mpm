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

package party.morino.mpm.api.application.plugin.model.integrity

/**
 * 整合性検証で使用したハッシュの出所
 *
 * 検証がどの情報源のハッシュに対して行われたかを表す。
 */
enum class IntegritySource {
    /** リポジトリ（Modrinth/Hangar等）がAPIで提供したハッシュ */
    REMOTE,

    /** 以前のインストール時にメタデータへ保存したハッシュ（trust-on-first-use） */
    STORED
}