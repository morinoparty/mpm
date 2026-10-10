/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils

import org.bukkit.Server
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.java.JavaPlugin
import party.morino.mpm.api.application.plugin.DeferredJarDeletion
import java.io.File

/**
 * 更新で不要になった旧JARを片付ける
 *
 * 通常はその場で削除するが、次の場合は [DeferredJarDeletion] に削除を予約してサーバー停止時に回す。
 * - 稼働中のプラグイン（mpm 自身を含む）が読み込んでいるJAR: 実行中のJARを消すとクラスローダー経由のリソース読み込みが壊れる
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
    if (isLoadedJar(oldFile, loadedPluginJars(plugin.server))) {
        // 他のプラグインも、停止するまではリソースを旧JARから読み込むため残しておく
        logger.info("旧JAR ${oldFile.name} は稼働中のプラグインが読み込んでいるため、サーバー停止時に削除します")
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
 *
 * Paper が再マッピングしたプラグインは plugins/.paper-remapped/ 配下のJARが返るため、
 * plugins/ 直下のJARとは一致しない（そのJARは消しても稼働中のプラグインに影響しない）。
 */
internal fun runningJarOf(plugin: Plugin): File? =
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
 * 読み込まれているすべてのプラグイン（無効化済みを含む）のJARを返す
 *
 * 無効化済みのプラグインもクラスローダーがJARを参照し続けるため含める。
 *
 * @param server サーバー
 * @return 各プラグインの読み込み元JAR（取得できないものは除く）
 */
internal fun loadedPluginJars(server: Server): List<File> = loadedPluginJars(server.pluginManager.plugins.asList())

/**
 * 指定したプラグインのJARを返す
 *
 * @param plugins 対象のプラグイン
 * @return 各プラグインの読み込み元JAR（取得できないものは除く）
 */
internal fun loadedPluginJars(plugins: Collection<Plugin>): List<File> = plugins.mapNotNull { runningJarOf(it) }

/**
 * JARが、読み込まれているプラグインのJARのいずれかと同じファイルかを判定する純粋なロジック
 *
 * @param jarFile 判定するJAR
 * @param loadedJars 読み込まれているプラグインのJAR
 * @return いずれかと同じファイルなら true
 */
internal fun isLoadedJar(
    jarFile: File,
    loadedJars: Collection<File>
): Boolean = loadedJars.any { isSameFile(it, jarFile) }

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

/**
 * 実行中の mpm 自身のJARが、既に指定バージョンで配置されているかを判定する
 *
 * mpm 自身は `latest` で管理されるため、`mpm install` のたびに同じバージョンの取り直しが走る。
 * 取り直すと実行中のJARを無駄に退避・上書きすることになり、手元でビルドしたJARでは
 * ハッシュ不一致で毎回失敗してしまうため、install 側はこの判定が真なら何もしない。
 *
 * @param plugin mpm 自身
 * @param pluginsDir pluginsディレクトリ
 * @param pluginName インストール対象のプラグイン名
 * @param recordedVersion メタデータに記録された現在のバージョン（raw）
 * @param recordedFileName メタデータに記録されたJARのファイル名
 * @param version インストールしようとしているバージョン（raw）
 */
internal fun isRunningSelfAt(
    plugin: JavaPlugin,
    pluginsDir: File,
    pluginName: String,
    recordedVersion: String?,
    recordedFileName: String?,
    version: String
): Boolean {
    // mpm 自身以外、またはバージョンが変わる場合は通常どおりインストールする
    if (pluginName != plugin.name || recordedVersion != version || recordedFileName == null) return false
    // 記録されたJARが実際に実行中のJARである場合だけ、配置済みとみなす
    val runningJar = runningJarOf(plugin) ?: return false
    return isSameFile(runningJar, File(pluginsDir, recordedFileName))
}