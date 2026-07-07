/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils

import party.morino.mpm.api.application.lock.LockService
import java.util.logging.Logger

/**
 * ロックファイル（mpm-lock.yaml）を再生成する。失敗しても例外は伝播させず警告ログのみ出す。
 *
 * install/update/add/remove/uninstall などの状態変更コマンドの成功後に呼び出し、
 * ロックファイルを実際のインストール状態に追従させる。ロック生成の失敗は本処理の
 * 成否に影響しないため、警告扱いとして握り潰す。
 *
 * @param logger 失敗時の警告出力に使用するロガー
 */
suspend fun LockService.regenerateQuietly(logger: Logger) {
    regenerate().onLeft { error ->
        logger.warning("ロックファイルの再生成に失敗しました: ${error.message}")
    }
}