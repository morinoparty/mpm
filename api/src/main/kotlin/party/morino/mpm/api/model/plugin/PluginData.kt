/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
and related and neighboring rights to this software to the public domain worldwide.
This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.model.plugin

import kotlinx.serialization.Serializable

/**
 * プラグインのデータを表すドメインモデル
 * BukkitとPaperの2つのタイプがある
 */
@Serializable
sealed class PluginData {
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
        val name: String = "",
        val version: String = "",
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
        val name: String = "",
        val version: String = "",
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
}