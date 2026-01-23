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
     * @return 成功時は作成されたメタデータ、失敗時はエラーメッセージ
     */
    suspend fun createMetadata(
        pluginName: String,
        repository: RepositoryConfig,
        versionData: VersionData,
        action: String = "add"
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
}