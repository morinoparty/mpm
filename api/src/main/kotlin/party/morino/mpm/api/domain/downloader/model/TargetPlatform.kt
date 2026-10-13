/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.downloader.model

import party.morino.mpm.api.model.plugin.PluginData

/**
 * mpm が動作しているサーバーの種類
 *
 * ダウンロード時に上流APIへ渡すローダー／プラットフォームの指定と、
 * ダウンロードしたJARがこのサーバーで動くかどうかの判定に使う。
 *
 * @property modrinthLoaders Modrinth のバージョン一覧APIに渡す loaders の値
 * @property hangarPlatform Hangar のダウンロードで優先するプラットフォーム名
 */
enum class TargetPlatform(
    val modrinthLoaders: List<String>,
    val hangarPlatform: String
) {
    /** Paper（Bukkit 系）サーバー */
    PAPER(listOf("paper", "spigot"), "PAPER"),

    /** Velocity プロキシ */
    VELOCITY(listOf("velocity"), "VELOCITY");

    /**
     * プラグイン記述子がこのプラットフォーム向けかを判定する
     *
     * @param pluginData JARから読み取ったプラグイン記述子
     * @return このプラットフォームで読み込めるプラグインなら true
     */
    fun supports(pluginData: PluginData): Boolean =
        when (pluginData) {
            // Paper は plugin.yml（Bukkit）と paper-plugin.yml の両方を読み込める
            is PluginData.BukkitPluginData, is PluginData.PaperPluginData -> this == PAPER
            // velocity-plugin.json は Velocity だけが読み込む
            is PluginData.VelocityPluginData -> this == VELOCITY
        }
}