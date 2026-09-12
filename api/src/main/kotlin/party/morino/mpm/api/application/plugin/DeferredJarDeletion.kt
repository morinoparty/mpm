/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.application.plugin

import arrow.core.Either
import java.io.File

/**
 * 今すぐ削除できないJARを、後で削除するために予約しておくサービス
 *
 * 代表的なのは mpm 自身の更新である。実行中のJARをディスクから消すと、
 * クラスローダー経由のリソース読み込み（kotlin-reflect の builtins など）が
 * 開き直しに失敗して動作が止まるため、旧JARはサーバー停止まで残しておく必要がある。
 * Windows のように実行中のJARを削除できない環境でも同じ仕組みで後始末する。
 */
interface DeferredJarDeletion {
    /**
     * JARの削除を予約する
     *
     * 予約はファイルに永続化され、サーバーがクラッシュしても次回起動時に処理される。
     *
     * @param jarFile 削除を予約するJAR
     * @return 予約の永続化に失敗した場合はその理由
     */
    fun schedule(jarFile: File): Either<String, Unit>

    /**
     * JARの削除予約を取り消す
     *
     * 同じファイル名のJARを配置し直した場合（ロールバックなど）に、
     * 新しく置いたJARが停止時に削除されてしまわないように呼び出す。
     *
     * @param jarFile 予約を取り消すJAR
     */
    fun cancel(jarFile: File)

    /**
     * 予約済みのJARをまとめて削除する（サーバー停止時に呼び出す）
     *
     * @return 実際に削除できたJAR
     */
    fun deleteScheduled(): List<File>

    /**
     * 起動時に、前回削除しきれなかったJARを片付ける
     *
     * 削除予約されたJARを Paper がこちらとして読み込んでしまった場合（クラッシュ後に
     * 新旧のJARが並んだ状態で旧側が選ばれた場合）は削除せず、警告だけ出して予約を残す。
     *
     * @param runningJar 現在実行中の mpm 自身のJAR
     */
    fun cleanupOnStartup(runningJar: File)
}