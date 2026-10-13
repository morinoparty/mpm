/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.platform

import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import java.io.File

/**
 * mpm のプラグイン管理イベントを、各プラットフォームのイベントシステムへ発火する
 *
 * 共通ロジックはプラットフォームのイベントクラスを直接扱えないため、このインターフェースを介して発火する。
 * Paper は既存の Bukkit イベント（party.morino.mpm.event.*）を、Velocity は独自のイベントを組み立てて発火する。
 *
 * キャンセル可能なイベントは、リスナーの処理が終わるまで待ってから「続行してよいか」を返す。
 */
interface MpmEventDispatcher {
    /**
     * プラグインを管理対象に追加する前に発火する
     *
     * @return キャンセルされなかった場合 true
     */
    suspend fun firePluginAdd(
        repositoryPlugin: RepositoryPlugin,
        versionSpecifier: VersionSpecifier,
        repositoryType: String,
        repositoryId: String
    ): Boolean

    /**
     * プラグインをインストールする前に発火する
     *
     * @return キャンセルされなかった場合 true
     */
    suspend fun firePluginInstall(
        repositoryPlugin: RepositoryPlugin,
        version: String,
        repositoryType: String,
        repositoryId: String
    ): Boolean

    /**
     * プラグインを管理対象から除外する前に発火する
     *
     * @return キャンセルされなかった場合 true
     */
    suspend fun firePluginRemove(installedPlugin: InstalledPlugin): Boolean

    /**
     * プラグインをアンインストールする前に発火する
     *
     * @param jarFile 削除対象のJAR（見つからない場合は null）
     * @return キャンセルされなかった場合 true
     */
    suspend fun firePluginUninstall(
        installedPlugin: InstalledPlugin,
        jarFile: File?
    ): Boolean

    /**
     * プラグインを更新（バージョン切り替えを含む）する前に発火する
     *
     * @return キャンセルされなかった場合 true
     */
    suspend fun firePluginUpdate(
        installedPlugin: InstalledPlugin,
        beforeVersion: VersionSpecifier,
        targetVersion: VersionSpecifier
    ): Boolean

    /**
     * プラグインをロックする前に発火する
     *
     * @return キャンセルされなかった場合 true
     */
    suspend fun firePluginLock(
        installedPlugin: InstalledPlugin,
        currentVersion: String
    ): Boolean

    /**
     * プラグインのロックを解除する前に発火する
     *
     * @return キャンセルされなかった場合 true
     */
    suspend fun firePluginUnlock(
        installedPlugin: InstalledPlugin,
        currentVersion: String
    ): Boolean

    /**
     * plugins/ 直下のJARを削除した（または削除を予約した）後に発火する（キャンセル不可）
     *
     * @param deferred 即時削除できずサーバー停止時の削除に回した場合 true
     */
    suspend fun firePluginJarDelete(
        fileName: String,
        jarFile: File,
        deferred: Boolean
    )

    /**
     * 定期チェックで新しいバージョンを検出したときに発火する（キャンセル不可）
     */
    suspend fun firePluginOutdated(
        installedPlugin: InstalledPlugin,
        currentVersion: String,
        latestVersion: String
    )
}