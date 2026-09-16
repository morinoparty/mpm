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
import party.morino.mpm.api.application.model.file.JarDeletionResult
import party.morino.mpm.api.application.model.file.JarFileInfo
import party.morino.mpm.api.shared.error.MpmError

/**
 * plugins/ ディレクトリ直下のJARファイルを直接操作するサービス
 *
 * [PluginLifecycleService.uninstall] と異なり、mpm.json やメタデータには一切触れず、
 * ファイルそのものだけを扱う。管理対象外のJARや、自己更新で残ってしまった旧JARのように
 * mpm の管理情報と結びつかないファイルを片付けるための入り口。
 */
interface PluginJarFileService {
    /**
     * plugins/ 直下のJARファイルを一覧する
     *
     * サブディレクトリは辿らない。plugin.yml を持たないJARや壊れたJARも
     * （プラグイン名を null にして）一覧に含める。[deleteJar] で削除できる対象そのものを返すため。
     *
     * @return ファイル名順のJAR一覧
     */
    fun listJars(): List<JarFileInfo>

    /**
     * plugins/ 直下のJARファイルを削除する
     *
     * 削除できるのは plugins/ 直下にある拡張子 .jar のファイルに限る。
     * サブディレクトリやパス区切りを含む指定は受け付けない。
     * 実行中の mpm 自身のJARが指定された場合は即時削除せず、サーバー停止時の削除を予約する。
     * 削除（または予約）が完了すると PluginJarDeleteEvent が発火し、Webhook 通知の対象になる。
     *
     * @param fileName 削除するファイル名（plugins/ からの相対。例: "mpm_0.0.25.jar"）
     * @return 削除結果。ファイル名が不正なら [MpmError.FileError.InvalidFileName]、
     *         存在しなければ [MpmError.FileError.NotFound]
     */
    suspend fun deleteJar(fileName: String): Either<MpmError, JarDeletionResult>
}