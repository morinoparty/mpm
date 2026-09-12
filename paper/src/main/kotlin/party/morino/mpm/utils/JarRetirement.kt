/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils

import org.bukkit.plugin.java.JavaPlugin
import party.morino.mpm.api.application.plugin.DeferredJarDeletion
import java.io.File

/**
 * 更新で不要になった旧JARを片付ける
 *
 * 通常はその場で削除するが、次の場合は [DeferredJarDeletion] に削除を予約してサーバー停止時に回す。
 * - mpm 自身の更新: 実行中のJARを消すとクラスローダー経由のリソース読み込みが壊れる
 * - 削除に失敗した場合: Windows など実行中のJARをロックする環境
 *
 * @param oldFile 不要になった旧JAR
 * @param pluginName 更新対象のプラグイン名
 * @param plugin mpm 自身（自己更新の判定とログ出力に使用）
 * @param deferredJarDeletion 削除予約サービス
 */
internal fun retireOldJar(
    oldFile: File,
    pluginName: String,
    plugin: JavaPlugin,
    deferredJarDeletion: DeferredJarDeletion
) {
    val logger = plugin.logger
    if (isSelfUpdate(oldFile, pluginName, plugin)) {
        logger.info("mpm 自身の更新のため、旧JAR ${oldFile.name} はサーバー停止時に削除します")
        deferredJarDeletion.schedule(oldFile).onLeft { logger.warning("旧JAR ${oldFile.name} の削除予約に失敗しました: $it") }
        return
    }
    if (oldFile.delete()) return
    // 実行中のJARがロックされている環境（Windows など）では即時削除できないため予約に回す
    logger.warning("旧JAR ${oldFile.name} を削除できなかったため、サーバー停止時に削除します")
    deferredJarDeletion.schedule(oldFile).onLeft { logger.warning("旧JAR ${oldFile.name} の削除予約に失敗しました: $it") }
}

/**
 * 旧JARが mpm 自身のものかを判定する
 *
 * プラグイン名の一致に加え、実際に実行中のJAR（クラスの読み込み元）と同じファイルかも確認する。
 * mpm.json 上の名前が plugin.yml と異なっていても、実行中のJARを消してしまわないようにするため。
 */
private fun isSelfUpdate(
    oldFile: File,
    pluginName: String,
    plugin: JavaPlugin
): Boolean = pluginName == plugin.name || runningJarOf(plugin)?.let { isSameFile(it, oldFile) } == true

/**
 * プラグインクラスの読み込み元JARを返す（取得できない場合は null）
 */
internal fun runningJarOf(plugin: JavaPlugin): File? =
    try {
        plugin::class.java.protectionDomain
            ?.codeSource
            ?.location
            ?.toURI()
            ?.let { File(it) }
    } catch (_: Exception) {
        null
    }

/**
 * 2つのパスが同じファイルを指すかを判定する（シンボリックリンクや相対パスの違いを吸収する）
 */
internal fun isSameFile(
    a: File,
    b: File
): Boolean =
    try {
        a.canonicalFile == b.canonicalFile
    } catch (_: Exception) {
        a.absoluteFile == b.absoluteFile
    }