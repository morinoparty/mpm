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
 * 代表的なのは、稼働中のプラグイン（mpm 自身を含む）が読み込んでいるJARの更新・削除である。
 * 実行中のJARをディスクから消すと、クラスローダー経由のリソース読み込み（kotlin-reflect の builtins など）が
 * 開き直しに失敗して動作が止まるため、旧JARはそのプラグインが停止するまで残しておく必要がある。
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
     * JARの削除が予約済みかどうかを返す
     *
     * @param jarFile 確認するJAR
     * @return 予約済みなら true
     */
    fun isScheduled(jarFile: File): Boolean

    /**
     * 予約済みのJARのうち、有効なプラグインが読み込んでいないものを削除する（mpm の停止時に呼び出す）
     *
     * mpm より後に停止するプラグインのJARは、ここでは削除せずに予約に残す。
     * それらは [installShutdownHook] で登録したフックが、JVM の終了時に削除する。
     *
     * @return 実際に削除できたJAR
     */
    fun deleteScheduled(): List<File>

    /**
     * JVM の終了時に、残っている予約済みのJARを削除するフックを登録する（mpm の起動時に呼び出す）
     *
     * フックは、予約済みのJARを読み込んでいるプラグインがすべて停止するのを待ってから削除する。
     * SIGTERM などで停止処理とフックが並行して走る場合でも、停止前のプラグインのJARを消さないため。
     */
    fun installShutdownHook()

    /**
     * 起動時に、前回削除しきれなかったJARを片付ける
     *
     * 削除予約されたJARを Paper が読み込んでしまった場合（クラッシュ後に
     * 新旧のJARが並んだ状態で旧側が選ばれた場合）は削除せず、警告だけ出して予約を残す。
     *
     * @param loadedJars 現在読み込まれているプラグインのJAR
     */
    fun cleanupOnStartup(loadedJars: Collection<File>)
}