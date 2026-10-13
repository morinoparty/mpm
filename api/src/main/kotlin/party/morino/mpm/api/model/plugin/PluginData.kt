/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.model.plugin

import kotlinx.serialization.Serializable

/**
 * プラグインのデータを表すドメインモデル
 * Bukkit・Paper・Velocity の3つのタイプがある
 */
@Serializable
sealed class PluginData {
    /**
     * プラグイン名（Velocity の場合はプラグインID）
     *
     * 種類ごとの分岐をせずに名前を参照できるよう、基底クラスで公開する
     */
    abstract val name: String

    /**
     * プラグインのバージョン
     */
    abstract val version: String

    /**
     * Bukkit/Spigot プラグインのデータモデル
     * @property name プラグイン名
     * @property version プラグインのバージョン
     * @property main メインクラスのパス
     * @property description プラグインの説明
     * @property author 作者
     * @property website ウェブサイト
     * @property apiVersion APIバージョン
     * @property depend 必須依存プラグインのリスト
     * @property softDepend オプション依存プラグインのリスト
     * @property loadBefore このプラグインより先に読み込むべきプラグインのリスト
     */
    @Serializable
    data class BukkitPluginData(
        override val name: String = "",
        override val version: String = "",
        val main: String = "",
        val description: String = "",
        val author: String = "",
        val website: String = "",
        val apiVersion: String = "",
        val depend: List<String> = emptyList(),
        val softDepend: List<String> = emptyList(),
        val loadBefore: List<String> = emptyList()
    ) : PluginData()

    /**
     * Paper プラグインのデータモデル
     * @property name プラグイン名
     * @property version プラグインのバージョン
     * @property main メインクラスのパス
     * @property description プラグインの説明
     * @property apiVersion APIバージョン
     * @property bootstrapper ブートストラッパー
     * @property loader ローダー
     * @property author 作者
     * @property website ウェブサイト
     * @property depend 必須依存プラグイン（server/bootstrap両方）
     * @property softDepend オプション依存プラグイン（server/bootstrap両方）
     * @property loadBefore このプラグインより先に読み込むべきプラグイン（server/bootstrap両方）
     */
    @Serializable
    data class PaperPluginData(
        override val name: String = "",
        override val version: String = "",
        val main: String = "",
        val description: String = "",
        val apiVersion: String = "",
        val bootstrapper: String = "",
        val loader: String = "",
        val author: String = "",
        val website: String = "",
        val depend: List<String> = emptyList(),
        val softDepend: List<String> = emptyList(),
        val loadBefore: List<String> = emptyList()
    ) : PluginData()

    /**
     * Velocity プラグインのデータモデル（velocity-plugin.json）
     *
     * Velocity ではプラグインを ID（小文字英数字・ハイフン・アンダースコア）で識別するため、
     * [name] には ID を入れ、表示名は [displayName] に分けて保持する。
     *
     * @property name プラグインID
     * @property version プラグインのバージョン
     * @property main メインクラスのパス
     * @property displayName 表示名（未指定の場合は空文字）
     * @property description プラグインの説明
     * @property authors 作者のリスト
     * @property url ウェブサイト
     * @property dependencies 依存プラグインのリスト（必須・オプションの両方）
     */
    @Serializable
    data class VelocityPluginData(
        override val name: String = "",
        override val version: String = "",
        val main: String = "",
        val displayName: String = "",
        val description: String = "",
        val authors: List<String> = emptyList(),
        val url: String = "",
        val dependencies: List<VelocityDependency> = emptyList()
    ) : PluginData() {
        /**
         * 必須依存プラグインのID
         */
        val depend: List<String>
            get() = dependencies.filterNot { it.optional }.map { it.id }

        /**
         * オプション依存プラグインのID
         */
        val softDepend: List<String>
            get() = dependencies.filter { it.optional }.map { it.id }
    }
}