/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.core.plugin

import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.core.plugin.DownloaderRepository
import party.morino.mpm.api.core.plugin.PluginInstallUseCase
import party.morino.mpm.api.core.plugin.PluginRepository
import party.morino.mpm.api.model.plugin.PluginData
import party.morino.mpm.api.utils.PluginDataUtils
import java.io.File

/**
 * プラグインのインストールに関するユースケースの実装
 * 依存性はKoinによって注入される
 */
class PluginInstallUseCaseImpl :
    PluginInstallUseCase,
    KoinComponent {
    // Koinによる依存性注入
    private val downloaderRepository: DownloaderRepository by inject()
    private val pluginRepository: PluginRepository by inject()
    private val plugin: JavaPlugin by inject()

    /**
     * リポジトリURLからプラグインをインストール
     * @param repositoryUrl リポジトリURL
     * @param number ダウンロードする数（複数ファイルがある場合）
     * @return インストールに成功した場合はtrue
     */
    override suspend fun installPlugin(
        repositoryUrl: String,
        number: Int?
    ): Boolean {
        // リポジトリURLを正規化
        val normalizedUrl =
            if (repositoryUrl.endsWith("/")) {
                repositoryUrl.dropLast(1)
            } else {
                repositoryUrl
            }

        // プラグインをダウンロード
        val downloadedFile = downloaderRepository.downloadLatest(normalizedUrl, number) ?: return false

        val pluginData = PluginDataUtils.getPluginData(downloadedFile) ?: return false

        val installedPluginName =
            if (pluginData is PluginData.BukkitPluginData) {
                pluginData.name
            } else {
                plugin.name
            }

        // すでに、同じ名前のプラグインがmanageされているか確認
        val installedPlugin = pluginRepository.getManagedPluginData(installedPluginName)
        if (installedPlugin != null) {
            // すでにインストールされている
            return false
        }

        // プラグインをインストール

        return true
    }

    /**
     * ファイルからプラグインをインストール
     * @param file プラグインファイル
     * @return インストールに成功した場合はtrue
     */
    override suspend fun installPluginFromFile(file: File): Boolean {
        // プラグインファイルからメタデータを抽出

        // プラグインをロード・有効化の処理を実装

        return true
    }
}