/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * ダウンロード済みのJARを配置先へ安全に置き換える
 *
 * 置換先と同じディレクトリに一時ファイル（`<配置先>.tmp`）を作ってから [Files.move] で移動する。
 * 同一ファイルシステム上の move はリネーム相当のため、既存JARは「旧内容」か「新内容」のどちらかにしかならず、
 * 途中で失敗しても切り詰められた壊れたJARが plugins/ に残らない。
 * （`copyTo(overwrite = true)` は既存ファイルを先に切り詰めるため、書き込み中の失敗で壊れたJARが残ってしまう）
 *
 * 成功・失敗のどちらでも、一時ファイルとダウンロード済み一時ファイルの両方を必ず削除する。
 *
 * @param downloadedFile ダウンロード済みの一時ファイル（呼び出し後は削除される）
 * @param targetFile 配置先のJARファイル
 * @return 成功時はUnit、失敗時は日本語のエラーメッセージ
 */
internal fun replaceJarAtomically(
    downloadedFile: File,
    targetFile: File
): Either<String, Unit> {
    // 配置先と同じディレクトリに置くことで、move が同一ファイルシステム内のリネームになる
    val stagedFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
    return try {
        // 一時ファイルへ書き出す。ここで失敗しても配置先の既存JARは無傷のまま
        downloadedFile.copyTo(stagedFile, overwrite = true)
        moveIntoPlace(stagedFile, targetFile)
        Unit.right()
    } catch (e: Exception) {
        "プラグインファイルの移動に失敗しました: ${e.message}".left()
    } finally {
        // 中間ファイルを残さない（失敗時に /tmp や plugins/ にゴミが残るのを防ぐ）
        stagedFile.delete()
        downloadedFile.delete()
    }
}

/**
 * ステージング済みファイルを配置先へ移動する
 *
 * まずアトミックな移動を試し、ファイルシステムが対応していない場合のみ置換移動へフォールバックする。
 *
 * @param stagedFile 配置先と同じディレクトリにあるステージング済みファイル
 * @param targetFile 配置先のファイル
 */
private fun moveIntoPlace(
    stagedFile: File,
    targetFile: File
) {
    try {
        Files.move(
            stagedFile.toPath(),
            targetFile.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING
        )
    } catch (_: Exception) {
        // ATOMIC_MOVE 非対応のファイルシステムでは通常の置換移動にフォールバックする
        Files.move(stagedFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
}