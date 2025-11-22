/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.core.plugin

import java.io.File

/**
 * プラグインのインストールに関するユースケース
 */
interface PluginInstallUseCase {
    /**
     * リポジトリURLからプラグインをインストール
     * @param repositoryUrl リポジトリURL
     * @param number ダウンロードする数（複数ファイルがある場合）
     * @return インストールに成功した場合はtrue
     */
    suspend fun installPlugin(
        repositoryUrl: String,
        number: Int?
    ): Boolean

    /**
     * ファイルからプラグインをインストール
     * @param file プラグインファイル
     * @return インストールに成功した場合はtrue
     */
    suspend fun installPluginFromFile(file: File): Boolean
}