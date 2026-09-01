/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
and related and neighboring rights to this software to the public domain worldwide.
This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.plugin.service

import arrow.core.Either
import party.morino.mpm.api.domain.downloader.model.VersionData
import party.morino.mpm.api.domain.plugin.dto.ManagedPluginDto
import party.morino.mpm.api.domain.repository.RepositoryConfig
import java.io.File

/**
 * プラグインメタデータの管理を行うインターフェース
 * metadata/xxx.yamlファイルの作成・更新・読み込み・保存を担当する
 */
interface PluginMetadataManager {
    /**
     * 新しいプラグインのメタデータを作成する
     *
     * @param pluginName プラグイン名
     * @param repository リポジトリ設定
     * @param versionData バージョン情報
     * @param action 実行したアクション（"add", "update" など）
     * @param channel このバージョンを解決した際のチャンネル名（"latest" / "beta" / "alpha"）。
     *   指定された場合、そのチャンネルの [ChannelConfig.versionModifier] が
     *   リポジトリルートの [RepositoryConfig.versionPattern] より優先して使用される。
     * @return 成功時は作成されたメタデータ、失敗時はエラーメッセージ
     */
    suspend fun createMetadata(
        pluginName: String,
        repository: RepositoryConfig,
        versionData: VersionData,
        action: String = "add",
        channel: String? = null
    ): Either<String, ManagedPluginDto>

    /**
     * 既存のメタデータを更新する
     *
     * @param pluginName プラグイン名
     * @param versionData 新しいバージョン情報（インストールするバージョン）
     * @param latestVersionData 最新バージョン情報（latestフィールドの更新用）
     * @param action 実行したアクション（"update", "install" など）
     * @return 成功時は更新されたメタデータ、失敗時はエラーメッセージ
     */
    suspend fun updateMetadata(
        pluginName: String,
        versionData: VersionData,
        latestVersionData: VersionData,
        action: String = "update"
    ): Either<String, ManagedPluginDto>

    /**
     * メタデータファイルからプラグインメタデータを読み込む
     *
     * @param pluginName プラグイン名
     * @return 成功時は読み込まれたメタデータ、失敗時はエラーメッセージ
     */
    fun loadMetadata(pluginName: String): Either<String, ManagedPluginDto>

    /**
     * プラグインメタデータをファイルに保存する
     *
     * @param pluginName プラグイン名
     * @param metadata 保存するメタデータ
     * @return 成功時はUnit、失敗時はエラーメッセージ
     */
    fun saveMetadata(
        pluginName: String,
        metadata: ManagedPluginDto
    ): Either<String, Unit>

    /**
     * メタデータファイルを退避して作り直してよいかを事前に判定する（副作用なし）
     *
     * ディスク上のファイルが現行スキーマ版数より新しい場合、退避して作り直すと
     * 「未知の設定を含む有効なファイル」を現行版数へ実質的に巻き戻すことになる。
     * これは [saveMetadata] のダウングレード防止ガードを退避経由で迂回する経路であり、
     * 未来版数のファイルは「破損」ではなく「このmpmでは解釈できないだけ」なので退避してはならない。
     *
     * 実際に退避する [quarantineMetadata] の内部でも同じ判定を行っているが、
     * 退避はダウンロードやJAR差し替えの後に遅延実行されるため、
     * 呼び出し側は「まだ何も壊していない段階」でこれを呼んで中止できるようにする。
     *
     * @param pluginName プラグイン名
     * @return 退避・作り直してよい場合はUnit、未来版数のため中止すべき場合はその理由
     */
    fun ensureMetadataReplaceable(pluginName: String): Either<String, Unit>

    /**
     * 読み込めなくなったメタデータファイルを退避（隔離）する
     *
     * `metadata/<プラグイン名>.yaml` を `metadata/<プラグイン名>.yaml.corrupt` へ移動する。
     * 退避先が既に存在する場合は `.corrupt.1`, `.corrupt.2` と連番を振り、
     * 過去に退避したファイルを上書きしない。
     *
     * 破損メタデータをそのまま作り直すと `lock: true` などの設定が無音で失われるため、
     * 作り直す前にこれを呼んで原本を残す。移動に失敗した場合は原本を削除せずエラーを返すので、
     * 呼び出し側は作り直しを中止すること。
     *
     * ディスク上のファイルが現行スキーマ版数より新しい場合は退避せずエラーを返す
     * （[ensureMetadataReplaceable] と同じ判定。詳しい理由はそちらのKDocを参照）。
     *
     * @param pluginName プラグイン名
     * @return 退避した場合はその退避先ファイル、対象ファイルが存在しない場合はnull、失敗時はエラーメッセージ
     */
    fun quarantineMetadata(pluginName: String): Either<String, File?>

    /**
     * 退避（隔離）したメタデータファイルを元の場所へ戻す
     *
     * 退避はしたものの、その後の保存に失敗して作り直しが完了しなかった場合に呼ぶ。
     * 戻さないと `metadata/<プラグイン名>.yaml` が存在しないまま処理が終わり、
     * メタデータファイルの存在を前提とするロック判定などが無音で無効化されてしまう。
     *
     * 退避先の内容をそのまま元のパスへ移動し直すだけで、内容には一切手を加えない。
     * 元のパスに既にファイルがある場合は上書きせずエラーを返す
     * （読めないファイルで有効なファイルを潰す方が被害が大きいため）。
     *
     * @param pluginName プラグイン名
     * @param quarantinedFile [quarantineMetadata] が返した退避先ファイル
     * @return 戻せた場合はUnit、失敗時はエラーメッセージ
     */
    fun restoreQuarantinedMetadata(
        pluginName: String,
        quarantinedFile: File
    ): Either<String, Unit>

    /**
     * プラグインメタデータファイルを削除する
     *
     * @param pluginName プラグイン名
     * @return 成功時はUnit、失敗時はエラーメッセージ
     */
    fun deleteMetadata(pluginName: String): Either<String, Unit>
}