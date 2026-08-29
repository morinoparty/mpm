/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.mock.config

import party.morino.mpm.api.domain.config.PluginDirectory
import java.io.File

/**
 * 一時ディレクトリを基点とする[PluginDirectory]のテスト用実装
 *
 * テストリソース配下（src/test/resources）を書き換えてしまわないよう、
 * ファイルを書き込むテストではこちらを使用する。
 *
 * @param rootDirectory プラグインのルートディレクトリ（通常は@TempDirで生成した一時ディレクトリ）
 */
class TempPluginDirectory(
    private val rootDirectory: File
) : PluginDirectory {
    override fun getRootDirectory(): File = rootDirectory.ensureExists()

    override fun getPluginsDirectory(): File = rootDirectory.parentFile.ensureExists()

    override fun getMetadataDirectory(): File = rootDirectory.resolve("metadata").ensureExists()

    override fun getRepositoryDirectory(): File = rootDirectory.resolve("repository").ensureExists()

    override fun getBackupsDirectory(): File = rootDirectory.resolve("backups").ensureExists()

    override fun getCacheDirectory(): File = rootDirectory.resolve("cache").ensureExists()

    /**
     * ディレクトリが存在しない場合は作成する
     */
    private fun File.ensureExists(): File {
        if (!exists()) mkdirs()
        return this
    }
}
