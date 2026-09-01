/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
 * and related and neighboring rights to this software to the public domain worldwide.
 * This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin.metadata

import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import java.io.File
import java.util.logging.Logger

/**
 * 退避（隔離）した原本を元の場所へ戻し、利用者に見せる補足メッセージを組み立てる
 *
 * ## なぜ必要か
 * 読み込めないメタデータは `<名前>.yaml` から `<名前>.yaml.corrupt` へ退避してから作り直す。
 * 退避した後に作り直したメタデータの保存が失敗すると
 * （書き込み権限が無い・容量が足りない・原子的な置換に対応していない等）、
 * `<名前>.yaml` が存在しないまま処理が終わってしまう。
 * メタデータの存在を前提とするロック判定などが無音で無効化されるため、
 * 保存に失敗した時点で必ず原本を元の場所へ戻す。
 *
 * 「保存に成功してから退避する」という順序は取れない。保存先と退避元は同じパスであり、
 * 先に保存すると退避すべき原本そのものを上書きしてしまうためである。
 *
 * @param metadataManager メタデータ管理（復元の実処理を担当する）
 * @param logger 復元できなかった場合に手動復旧の手掛かりを残すロガー
 * @param pluginName プラグイン名
 * @param quarantinedFile 退避先ファイル。退避していない場合はnull
 * @return エラーメッセージへ連結する補足文。退避していない場合は空文字
 */
fun restoreQuarantinedMetadataOrWarn(
    metadataManager: PluginMetadataManager,
    logger: Logger,
    pluginName: String,
    quarantinedFile: File?
): String {
    // そもそも退避していなければ戻すものは無い（通常経路）
    if (quarantinedFile == null) return ""

    return metadataManager.restoreQuarantinedMetadata(pluginName, quarantinedFile).fold(
        { restoreError ->
            // 戻せなかった場合は現役のメタデータが不在のままになる。
            // 内容自体は退避先に残っているため、手作業で復旧できるよう絶対パス付きで記録する
            logger.severe(
                "メタデータの保存に失敗した後、退避した原本を元に戻せませんでした: $pluginName ($restoreError)。" +
                    "原本の内容は ${quarantinedFile.absolutePath} に残っています。" +
                    "ファイル名から '.corrupt' 以降（連番が付いている場合はそれも含めて）を取り除き、" +
                    "$pluginName.yaml という名前で手動で戻してください。"
            )
            " (退避した原本を戻せませんでした: $restoreError。原本は ${quarantinedFile.absolutePath} に残っています)"
        },
        {
            logger.warning("メタデータの保存に失敗したため、退避した原本を元に戻しました: $pluginName")
            " (退避した原本は元に戻しました)"
        }
    )
}