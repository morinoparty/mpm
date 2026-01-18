/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.config

import java.io.File

/**
 * プラグインのディレクトリを管理するインターフェース
 */
interface PluginDirectory {
    /**
     * プラグインのルートディレクトリを取得する
     * @return プラグインのルートディレクトリ
     */
    fun getRootDirectory(): File

    /**
     * プラグインディレクトリ（サーバーのpluginsフォルダ）を取得する
     * @return プラグインディレクトリ
     */
    fun getPluginsDirectory(): File

    /**
     * メタデータディレクトリを取得する
     * @return メタデータディレクトリ
     */
    fun getMetadataDirectory(): File

    /**
     * レポジトリのデータディレクトリを取得する
     * @return レポジトリのデータディレクトリ
     */
    fun getRepositoryDirectory(): File

    /**
     * バックアップディレクトリを取得する
     * @return バックアップディレクトリ
     */
    fun getBackupsDirectory(): File
}
