/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api

import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.application.plugin.PluginLifecycleService
import party.morino.mpm.api.application.plugin.PluginUpdateService
import party.morino.mpm.api.application.project.ProjectService
import party.morino.mpm.api.domain.config.ConfigManager
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import party.morino.mpm.api.domain.repository.RepositoryManager

/**
 * MinecraftPluginManagerのAPIインターフェース
 *
 * 外部プラグインやアドオンがMinecraftPluginManagerの機能にアクセスするための公開API
 * 各マネージャーへのアクセサを提供する
 */
interface MpmAPI {
    /**
     * 設定管理マネージャーを取得
     *
     * config.jsonの読み込み・再読み込みを担当するマネージャー
     *
     * @return ConfigManagerのインスタンス
     */
    fun getConfigManager(): ConfigManager

    /**
     * プラグインディレクトリマネージャーを取得
     *
     * プラグインディレクトリやデータディレクトリのパス管理を担当するマネージャー
     *
     * @return PluginDirectoryのインスタンス
     */
    fun getPluginDirectory(): PluginDirectory

    /**
     * プラグイン情報サービスを取得
     *
     * プラグインの情報取得やリスト表示を担当するサービス
     *
     * @return PluginInfoServiceのインスタンス
     */
    fun getPluginInfoService(): PluginInfoService

    /**
     * プラグインライフサイクルサービスを取得
     *
     * プラグインのインストール・削除・有効化/無効化を担当するサービス
     *
     * @return PluginLifecycleServiceのインスタンス
     */
    fun getPluginLifecycleService(): PluginLifecycleService

    /**
     * プラグイン更新サービスを取得
     *
     * プラグインの更新チェックとアップデートを担当するサービス
     *
     * @return PluginUpdateServiceのインスタンス
     */
    fun getPluginUpdateService(): PluginUpdateService

    /**
     * プラグインメタデータマネージャーを取得
     *
     * プラグインのメタデータ管理を担当するマネージャー
     *
     * @return PluginMetadataManagerのインスタンス
     */
    fun getPluginMetadataManager(): PluginMetadataManager

    /**
     * プロジェクトサービスを取得
     *
     * プロジェクトの初期化と管理を担当するサービス
     *
     * @return ProjectServiceのインスタンス
     */
    fun getProjectService(): ProjectService

    /**
     * リポジトリマネージャーを取得
     *
     * リポジトリソースの管理を担当するマネージャー
     *
     * @return RepositoryManagerのインスタンス
     */
    fun getRepositoryManager(): RepositoryManager
}