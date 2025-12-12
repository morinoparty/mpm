/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.core.plugin.usecase

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.core.plugin.PluginMetadataManager
import party.morino.mpm.api.core.plugin.UnlockPluginUseCase

/**
 * mpm unlockコマンドに関するユースケースの実装
 * 依存性はKoinによって注入される
 */
class UnlockPluginUseCaseImpl :
    UnlockPluginUseCase,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginMetadataManager: PluginMetadataManager by inject()

    /**
     * プラグインのロックを解除する
     * プラグインのメタデータからロックフラグを削除する
     *
     * @param pluginName プラグイン名
     * @return 成功時はUnit、失敗時はエラーメッセージ
     */
    override suspend fun unlockPlugin(pluginName: String): Either<String, Unit> {
        // メタデータを読み込む
        val metadata =
            pluginMetadataManager.loadMetadata(pluginName).getOrElse {
                return "プラグインのメタデータが見つかりません: $pluginName".left()
            }

        // 既にロック解除されている場合はエラー
        if (!metadata.mpmInfo.settings.lock) {
            return "プラグイン '$pluginName' はロックされていません。".left()
        }

        // ロックフラグを解除
        val updatedMetadata =
            metadata.copy(
                mpmInfo = metadata.mpmInfo.copy(
                    settings = metadata.mpmInfo.settings.copy(lock = false)
                )
            )

        // メタデータを保存
        pluginMetadataManager.saveMetadata(pluginName, updatedMetadata).getOrElse {
            return it.left()
        }

        return Unit.right()
    }
}