/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.cache

/**
 * キャッシュディレクトリのレイアウト定義
 *
 * plugins/mpm/cache/ 配下のサブディレクトリ名を一箇所に集約し、
 * キャッシュの書き込み側（HttpMetadataCacheImpl）と管理側（CacheManagerImpl）で共有する。
 */
object CacheDirectories {
    /** HTTPメタデータキャッシュのサブディレクトリ名 */
    const val METADATA: String = "metadata"
}