/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.scheduler

import arrow.core.Either
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.application.scheduler.OutdatedNotificationLedger
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.infrastructure.migration.AtomicFileWriter
import party.morino.mpm.utils.Utils
import java.io.File

/**
 * [OutdatedNotificationLedger] の実装
 *
 * プラグインのデータフォルダ直下の `outdated-notified.json` に
 * 「プラグイン名 -> 通知時の最新バージョン（raw）」を素の JSON オブジェクトとして保存する。
 * 書き込みは他の設定ファイルと同じく [AtomicFileWriter] に委ね、途中でサーバーが落ちても
 * 台帳が壊れないようにする。
 */
class OutdatedNotificationLedgerImpl :
    OutdatedNotificationLedger,
    KoinComponent {
    // Koinによる依存性注入
    private val plugin: JavaPlugin by inject()
    private val pluginDirectory: PluginDirectory by inject()

    companion object {
        // 台帳ファイル名（データフォルダ直下）
        private const val FILE_NAME = "outdated-notified.json"

        // JSON上の表現は {"PluginName": "1.2.3", ...} の平坦なオブジェクト
        private val serializer = MapSerializer(String.serializer(), String.serializer())
    }

    override suspend fun load(): Map<String, String> {
        val file = getLedgerFile()
        if (!file.exists()) return emptyMap()
        return try {
            Utils.json.decodeFromString(serializer, file.readText())
        } catch (e: Exception) {
            // 読めない台帳は空扱いにする（その回だけ通知が重複しうるが、通知が止まるよりはよい）
            plugin.logger.warning("$FILE_NAME を読み込めなかったため、通知済みの記録を無視します: ${e.message}")
            emptyMap()
        }
    }

    override suspend fun record(
        notified: Map<String, String>,
        managedPlugins: Set<String>
    ): Either<String, Unit> {
        // 既存の記録のうち管理下に残っているものだけを引き継ぎ、今回の通知で上書きする
        val merged = load().filterKeys { it in managedPlugins } + notified
        val content = Utils.json.encodeToString(serializer, merged)
        return AtomicFileWriter.write(getLedgerFile(), content)
    }

    /**
     * 台帳ファイルを取得する
     */
    private fun getLedgerFile(): File = File(pluginDirectory.getRootDirectory(), FILE_NAME)
}