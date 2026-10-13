/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.velocity.event.lifecycle

import com.velocitypowered.api.event.ResultedEvent
import party.morino.mpm.api.model.plugin.RepositoryPlugin

/**
 * プラグインのインストールが行われる際に発火するイベント
 *
 * プラグインファイルのダウンロード・配置処理の前に呼び出される。
 * 結果を拒否（denied）にすると、インストール処理を中止させることができる。
 *
 * @property repositoryPlugin インストール対象のリポジトリプラグイン
 * @property version インストールされる予定のバージョン
 * @property repositoryType リポジトリの種類（github, modrinth, spigotmc など）
 * @property repositoryId リポジトリID
 */
class PluginInstallEvent(
    val repositoryPlugin: RepositoryPlugin,
    val version: String,
    val repositoryType: String,
    val repositoryId: String
) : ResultedEvent<ResultedEvent.GenericResult> {
    // ハンドラーは別スレッドで順に呼ばれるため、結果の書き込みが他スレッドから見えるようにする
    @Volatile
    private var currentResult: ResultedEvent.GenericResult = ResultedEvent.GenericResult.allowed()

    /**
     * 現在の結果を返す（初期値は許可）
     */
    override fun getResult(): ResultedEvent.GenericResult = currentResult

    /**
     * 結果を設定する（denied にすると mpm は処理を中止する）
     *
     * @param result 新しい結果
     */
    override fun setResult(result: ResultedEvent.GenericResult) {
        currentResult = result
    }
}