/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.config.plugin

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * プラグインのバージョン指定方法を表すsealed class
 *
 * Minecraftプラグインには共通のsemverがないため、複数の指定方法をサポートする
 */
@Serializable
sealed class VersionSpecifier {
    /**
     * 固定バージョン指定
     *
     * 例: "1.0.0", "5.4.102"
     *
     * @property version バージョン文字列
     */
    @Serializable
    @SerialName("fixed")
    data class Fixed(val version: String) : VersionSpecifier()

    /**
     * 最新バージョン指定
     *
     * リポジトリから取得できる最新バージョンを使用する
     */
    @Serializable
    @SerialName("latest")
    data object Latest : VersionSpecifier()

    /**
     * タグ/チャンネル指定（将来実装予定）
     *
     * 例: "stable", "beta", "alpha"
     *
     * @property tag タグ名
     */
    @Serializable
    @SerialName("tag")
    data class Tag(val tag: String) : VersionSpecifier()

    /**
     * 正規表現パターン指定（将来実装予定）
     *
     * バージョン文字列が指定されたパターンにマッチする最新バージョンを使用する
     *
     * 例: "^5\\.4\\..*" → 5.4系の最新バージョン
     *
     * @property pattern 正規表現パターン
     */
    @Serializable
    @SerialName("pattern")
    data class Pattern(val pattern: String) : VersionSpecifier()

    /**
     * 同期バージョン指定
     *
     * 別のプラグインのバージョンに同期する
     * アドオンプラグインが親プラグインと同じバージョンを使用する場合に使用する
     *
     * 例: "sync:QuickShop" → QuickShopと同じバージョンを使用
     *
     * @property targetPlugin 同期対象のプラグイン名
     */
    @Serializable
    @SerialName("sync")
    data class Sync(val targetPlugin: String) : VersionSpecifier()
}
