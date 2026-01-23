/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.config

import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.domain.config.PluginDirectory
import java.io.File

/**
 * プラグインのディレクトリを管理する実装クラス
 * Koinによって依存性注入されるJavaPluginインスタンスを使用してディレクトリパスを取得する
 */
class PluginDirectoryImpl :
    PluginDirectory,
    KoinComponent {
    // Koinによる依存性注入でJavaPluginのインスタンスを取得
    private val plugin: JavaPlugin by inject()

    // プラグインのルートディレクトリ（dataFolder）をlazyで初期化
    private val rootDirectoryFile: File by lazy { plugin.dataFolder }

    // サーバーのpluginsディレクトリ（親フォルダ）をlazyで初期化
    private val pluginsDirectoryFile: File by lazy { plugin.dataFolder.parentFile }

    // プラグインをインストールするためのデータを保存するディレクトリをlazyで初期化
    private val repositoryDirectoryFile: File by lazy { File(rootDirectoryFile, "repository") }
    private val metadataDirectoryFile: File by lazy { File(rootDirectoryFile, "metadata") }
    private val backupsDirectoryFile: File by lazy { File(rootDirectoryFile, "backups") }

    /**
     * プラグインのルートディレクトリを取得する
     * ディレクトリが存在しない場合は作成する
     * @return プラグインのルートディレクトリ
     */
    override fun getRootDirectory(): File {
        // ディレクトリが存在しない場合は作成
        if (!rootDirectoryFile.exists()) {
            rootDirectoryFile.mkdirs()
        }
        return rootDirectoryFile
    }

    /**
     * プラグインディレクトリ（サーバーのpluginsフォルダ）を取得する
     * ディレクトリが存在しない場合は作成する
     * @return プラグインディレクトリ
     */
    override fun getPluginsDirectory(): File {
        // ディレクトリが存在しない場合は作成
        if (!pluginsDirectoryFile.exists()) {
            pluginsDirectoryFile.mkdirs()
        }
        return pluginsDirectoryFile
    }

    /**
     * メタデータディレクトリを取得する
     * ディレクトリが存在しない場合は作成する
     * @return メタデータディレクトリ
     */
    override fun getMetadataDirectory(): File {
        // ディレクトリが存在しない場合は作成
        if (!metadataDirectoryFile.exists()) {
            metadataDirectoryFile.mkdirs()
        }
        return metadataDirectoryFile
    }

    /**
     * レポジトリディレクトリを取得する
     * ディレクトリが存在しない場合は作成する
     * @return レポジトリディレクトリ
     */
    override fun getRepositoryDirectory(): File {
        // ディレクトリが存在しない場合は作成
        if (!repositoryDirectoryFile.exists()) {
            repositoryDirectoryFile.mkdirs()
        }
        return repositoryDirectoryFile
    }

    /**
     * バックアップディレクトリを取得する
     * ディレクトリが存在しない場合は作成する
     * @return バックアップディレクトリ
     */
    override fun getBackupsDirectory(): File {
        // ディレクトリが存在しない場合は作成
        if (!backupsDirectoryFile.exists()) {
            backupsDirectoryFile.mkdirs()
        }
        return backupsDirectoryFile
    }
}
