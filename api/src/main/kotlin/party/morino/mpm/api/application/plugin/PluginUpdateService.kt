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

package party.morino.mpm.api.application.plugin

import arrow.core.Either
import party.morino.mpm.api.application.model.UpdateResult
import party.morino.mpm.api.application.model.install.BulkInstallResult
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.shared.error.MpmError

/**
 * プラグイン更新サービス
 *
 * プラグインの更新・ロック/アンロック・一括インストールを担当する
 * 薄いファサードとして機能し、オーケストレーションのみを行う
 */
interface PluginUpdateService {
    /**
     * すべてのプラグインを更新する
     *
     * ロックされていないプラグインを最新バージョンに更新する
     *
     * @param force trueの場合、api-version非互換でも強制更新する
     * @param progressCallback 進捗メッセージを受け取るコールバック（MiniMessage形式）
     * @param skipIntegrity trueの場合、整合性検証の不一致を無視して更新を続行する
     * @return 更新結果一覧
     */
    suspend fun update(
        force: Boolean = false,
        progressCallback: ((String) -> Unit)? = null,
        skipIntegrity: Boolean = false
    ): Either<MpmError, List<UpdateResult>>

    /**
     * 指定プラグインを更新する
     *
     * 更新対象プラグインに同期している `sync:` プラグイン（子）があれば、
     * 親の更新後バージョンに追従して連動更新する。
     * 戻り値のリストは先頭が親の更新結果、以降が連動更新した子の結果となる。
     *
     * @param name プラグイン名
     * @param force trueの場合、api-version非互換でも強制更新する
     * @param skipIntegrity trueの場合、整合性検証の不一致を無視して更新を続行する
     * @return 更新結果一覧（親＋連動更新した子）
     */
    suspend fun update(
        name: PluginName,
        force: Boolean = false,
        skipIntegrity: Boolean = false
    ): Either<MpmError, List<UpdateResult>>

    /**
     * 管理下プラグインを指定バージョンに切り替える
     *
     * アップグレード・ダウングレードのどちらも本メソッドで扱う
     * （「このプラグインをバージョン X にする」という1つの操作として統一する）。
     *
     * 処理内容:
     * 1. 対象バージョンをリポジトリ上の実バージョン名に解決する
     * 2. jar差し替え前にサーバーバックアップを自動作成する（失敗しても処理は継続する）
     * 3. ダウンロードとハッシュ整合性検証を行い、jarを差し替える
     * 4. メタデータを更新し、履歴エントリを追記する
     * 5. mpm.json のバージョン指定を VersionSpecifier.Fixed に書き換える
     * 6. このプラグインに `sync:` で追従している子プラグインを、同じバージョンへ連動更新する
     * 7. mpm-lock.yaml を実インストール状態へ再生成する
     *
     * `sync:` 指定のプラグインは他プラグインへの追従が目的のため、切り替えを拒否する。
     *
     * @param name プラグイン名
     * @param version 切り替え先バージョン（raw / normalized のどちらでも解決を試みる）
     * @param force trueの場合、ロック済み・api-version非互換でも強制的に切り替える
     * @param skipIntegrity trueの場合、整合性検証の不一致を無視して続行する
     * @return 切り替え結果（oldVersion/newVersion を含む）
     */
    suspend fun switchVersion(
        name: PluginName,
        version: String,
        force: Boolean = false,
        skipIntegrity: Boolean = false
    ): Either<MpmError, UpdateResult>

    /**
     * 管理下プラグインを過去のバージョンへ切り戻す
     *
     * [switchVersion] の薄いラッパーであり、実処理はすべて [switchVersion] に委譲する。
     * [version] が null の場合のみ、メタデータの履歴から「直前のバージョン」を解決する。
     *
     * 履歴からの解決は「必ずより過去へ進む」規則に従う。具体的には、現在バージョンが履歴に
     * 最初に現れた位置より前の最後のエントリを採用する（現在バージョンが履歴に無い場合は、
     * 末尾から遡って現在と異なる最初のエントリ）。rollback 自身が履歴にエントリを追記するため、
     * 単純に末尾から遡ると2つのバージョンを往復してしまうのを避けるための規則である。
     * 遡れるエントリが無い場合はエラー（VersionResolutionFailed）を返す。
     *
     * @param name プラグイン名
     * @param version 切り戻し先バージョン。null の場合は履歴上の直前のバージョンを使用する
     * @param force trueの場合、ロック済み・api-version非互換でも強制的に切り戻す
     * @param skipIntegrity trueの場合、整合性検証の不一致を無視して続行する
     * @return 切り戻し結果（oldVersion/newVersion を含む）
     */
    suspend fun rollback(
        name: PluginName,
        version: String? = null,
        force: Boolean = false,
        skipIntegrity: Boolean = false
    ): Either<MpmError, UpdateResult>

    /**
     * mpm.jsonに記載されたすべてのプラグインを一括インストールする
     *
     * @param force trueの場合、api-version非互換でも強制インストールする
     * @param skipIntegrity trueの場合、整合性検証の不一致を無視してインストールを続行する
     * @param frozen trueの場合、mpm-lock.yamlに記録された正確なバージョンをインストールする
     *   （mpm.jsonのlatest/tag指定を無視した再現インストール。ロック未存在時はエラー）
     * @return 一括インストール結果
     */
    suspend fun installAll(
        force: Boolean = false,
        skipIntegrity: Boolean = false,
        frozen: Boolean = false
    ): Either<MpmError, BulkInstallResult>

    /**
     * プラグインをロックする
     *
     * ロックされたプラグインは自動更新の対象外になる
     *
     * @param name プラグイン名
     * @return 成功時はUnit
     */
    suspend fun lock(name: PluginName): Either<MpmError, Unit>

    /**
     * プラグインのロックを解除する
     *
     * @param name プラグイン名
     * @return 成功時はUnit
     */
    suspend fun unlock(name: PluginName): Either<MpmError, Unit>
}