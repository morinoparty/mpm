/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.core.plugin

import org.koin.core.component.KoinComponent
import party.morino.mpm.api.core.plugin.PluginInstallUseCase
import java.io.File

/**
 * プラグインのインストールに関するユースケースの実装
 * 依存性はKoinによって注入される
 */
class PluginInstallUseCaseImpl :
    PluginInstallUseCase,
    KoinComponent {
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