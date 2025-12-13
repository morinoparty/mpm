/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.core.config

import party.morino.mpm.api.config.plugin.ConfigData

/**
 * config.jsonの管理を行うインターフェース
 *
 * リポジトリソースやグローバル設定の読み込み・再読み込みを担当する
 */
interface ConfigManager {
    /**
     * 現在の設定を取得する
     *
     * @return 現在のConfigData
     */
    fun getConfig(): ConfigData

    /**
     * config.jsonを再読み込みする
     *
     * ファイルから設定を読み込み直し、内部状態を更新する
     */
    suspend fun reload()
}
