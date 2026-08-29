/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.command.manage.control

import org.bukkit.command.CommandSender
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.domain.cache.CacheManager
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.annotation.Switch
import revxrsal.commands.bukkit.annotation.CommandPermission
import java.text.DecimalFormat

/**
 * キャッシュコマンドのコントローラー
 * plugins/mpm/cache/ 配下のHTTPメタデータキャッシュの一覧・サイズ確認・削除を提供する
 */
@Command("mpm", "mpm cache")
@CommandPermission("mpm.command.cache")
class CacheCommand : KoinComponent {
    // KoinによるDI
    private val cacheManager: CacheManager by inject()

    companion object {
        // 一覧表示の最大件数（大量のエントリでチャットを埋めないための上限）
        private const val MAX_LISTED_ENTRIES = 15

        // URL表示の最大文字数（長いAPI URLでチャットが折り返されるのを防ぐ）
        private const val MAX_URL_LENGTH = 60
    }

    /**
     * キャッシュエントリの一覧を表示するコマンド
     * @param sender コマンド送信者
     */
    @Subcommand("cache list")
    fun list(sender: CommandSender) {
        cacheManager.list().fold(
            // 失敗時の処理
            { error ->
                sender.sendRichMessage("<red>${error.message}")
            },
            // 成功時の処理
            { entries ->
                if (entries.isEmpty()) {
                    sender.sendRichMessage("<gray>キャッシュはありません。")
                    return@fold
                }

                sender.sendRichMessage("<yellow>キャッシュ一覧: <white>${entries.size}<gray>件")
                entries.take(MAX_LISTED_ENTRIES).forEach { entry ->
                    // 期限切れのエントリは色分けして「cleanで消せる」ことを分かりやすくする
                    val statusLabel = if (entry.expired) "<red>[EXPIRED]" else "<green>[FRESH]"
                    sender.sendRichMessage(
                        "<gray>  $statusLabel <white>${shortenUrl(entry.url)} <gray>- " +
                            "<white>${formatFileSize(entry.sizeBytes)} <gray>- " +
                            "<white>${entry.fetchedAt}"
                    )
                }
                if (entries.size > MAX_LISTED_ENTRIES) {
                    sender.sendRichMessage("<gray>  ... 他 ${entries.size - MAX_LISTED_ENTRIES} 件")
                }
            }
        )
    }

    /**
     * キャッシュの合計サイズを表示するコマンド
     * @param sender コマンド送信者
     */
    @Subcommand("cache size")
    fun size(sender: CommandSender) {
        cacheManager.size().fold(
            // 失敗時の処理
            { error ->
                sender.sendRichMessage("<red>${error.message}")
            },
            // 成功時の処理
            { info ->
                sender.sendRichMessage(
                    "<yellow>キャッシュサイズ: <white>${formatFileSize(info.totalSizeBytes)} " +
                        "<gray>(${info.entryCount}ファイル)"
                )
                if (info.expiredEntryCount > 0) {
                    sender.sendRichMessage(
                        "<gray>期限切れエントリ: <white>${info.expiredEntryCount}<gray>件 " +
                            "(<white>/mpm cache clean --expired<gray> で削除できます)"
                    )
                }
            }
        )
    }

    /**
     * キャッシュを削除するコマンド
     * @param sender コマンド送信者
     * @param expiredOnly trueの場合はTTLを超過したエントリのみを削除する
     */
    @Subcommand("cache clean")
    fun clean(
        sender: CommandSender,
        @Switch("expired") expiredOnly: Boolean = false
    ) {
        cacheManager.clean(expiredOnly).fold(
            // 失敗時の処理
            { error ->
                sender.sendRichMessage("<red>${error.message}")
            },
            // 成功時の処理
            { result ->
                if (result.removedEntries == 0) {
                    sender.sendRichMessage("<gray>削除するキャッシュはありませんでした。")
                    return@fold
                }
                sender.sendRichMessage(
                    "<green>${result.removedEntries} 件のキャッシュを削除しました " +
                        "<gray>(${formatFileSize(result.freedBytes)} を解放)"
                )
            }
        )
    }

    /**
     * 長いURLを表示用に短縮する
     * @param url 表示対象のURL
     * @return 短縮したURL
     */
    private fun shortenUrl(url: String): String =
        if (url.length <= MAX_URL_LENGTH) url else "${url.take(MAX_URL_LENGTH)}..."

    /**
     * ファイルサイズを人間が読みやすい形式にフォーマットする
     * @param bytes バイト数
     * @return フォーマットされた文字列
     */
    private fun formatFileSize(bytes: Long): String {
        val df = DecimalFormat("#.##")
        return when {
            bytes >= 1_073_741_824 -> "${df.format(bytes / 1_073_741_824.0)} GB"
            bytes >= 1_048_576 -> "${df.format(bytes / 1_048_576.0)} MB"
            bytes >= 1_024 -> "${df.format(bytes / 1_024.0)} KB"
            else -> "$bytes B"
        }
    }
}