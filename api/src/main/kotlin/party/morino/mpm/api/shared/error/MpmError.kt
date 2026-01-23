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

package party.morino.mpm.api.shared.error

/**
 * MPM全体で使用される共通エラー型
 *
 * アプリケーション全体で一貫したエラーハンドリングを実現するためのsealed class
 */
sealed class MpmError {
    // エラーメッセージを取得
    abstract val message: String

    // プラグイン関連のエラー
    sealed class PluginError : MpmError() {
        // プラグインが見つからない
        data class NotFound(
            val pluginName: String
        ) : PluginError() {
            override val message: String = "Plugin not found: $pluginName"
        }

        // プラグインが既に存在する
        data class AlreadyExists(
            val pluginName: String
        ) : PluginError() {
            override val message: String = "Plugin already exists: $pluginName"
        }

        // プラグインがロックされている
        data class Locked(
            val pluginName: String
        ) : PluginError() {
            override val message: String = "Plugin is locked: $pluginName"
        }

        // プラグインがロックされていない
        data class NotLocked(
            val pluginName: String
        ) : PluginError() {
            override val message: String = "Plugin is not locked: $pluginName"
        }

        // プラグインが既にロックされている
        data class AlreadyLocked(
            val pluginName: String
        ) : PluginError() {
            override val message: String = "Plugin is already locked: $pluginName"
        }

        // 操作がキャンセルされた
        data class OperationCancelled(
            val pluginName: String,
            val operation: String
        ) : PluginError() {
            override val message: String = "Operation '$operation' cancelled for $pluginName"
        }

        // メタデータが見つからない
        data class MetadataNotFound(
            val pluginName: String
        ) : PluginError() {
            override val message: String = "Metadata not found for $pluginName"
        }

        // メタデータの保存に失敗
        data class MetadataSaveFailed(
            val pluginName: String,
            val reason: String
        ) : PluginError() {
            override val message: String = "Failed to save metadata for $pluginName: $reason"
        }

        // リポジトリが見つからない
        data class RepositoryNotFound(
            val pluginName: String
        ) : PluginError() {
            override val message: String = "Repository not found for $pluginName"
        }

        // 未対応のリポジトリタイプ
        data class UnsupportedRepository(
            val repoType: String
        ) : PluginError() {
            override val message: String = "Unsupported repository type: $repoType"
        }

        // プラグインが管理対象外
        data class NotManaged(
            val pluginName: String
        ) : PluginError() {
            override val message: String = "Plugin is not managed: $pluginName"
        }

        // バージョン解決エラー
        data class VersionResolutionFailed(
            val pluginName: String,
            val reason: String
        ) : PluginError() {
            override val message: String = "Failed to resolve version for $pluginName: $reason"
        }

        // インストールエラー
        data class InstallFailed(
            val pluginName: String,
            val reason: String
        ) : PluginError() {
            override val message: String = "Failed to install $pluginName: $reason"
        }

        // 追加エラー
        data class AddFailed(
            val pluginName: String,
            val reason: String
        ) : PluginError() {
            override val message: String = "Failed to add $pluginName: $reason"
        }

        // 削除エラー
        data class RemoveFailed(
            val pluginName: String,
            val reason: String
        ) : PluginError() {
            override val message: String = "Failed to remove $pluginName: $reason"
        }

        // アンインストールエラー
        data class UninstallFailed(
            val pluginName: String,
            val reason: String
        ) : PluginError() {
            override val message: String = "Failed to uninstall $pluginName: $reason"
        }

        // 更新エラー
        data class UpdateFailed(
            val pluginName: String,
            val reason: String
        ) : PluginError() {
            override val message: String = "Failed to update $pluginName: $reason"
        }
    }

    // プロジェクト関連のエラー
    sealed class ProjectError : MpmError() {
        // プロジェクトが初期化されていない
        data object NotInitialized : ProjectError() {
            override val message: String = "Project is not initialized. Run 'mpm init' first."
        }

        // プロジェクトが既に初期化されている
        data object AlreadyInitialized : ProjectError() {
            override val message: String = "Project is already initialized."
        }

        // 設定ファイルが見つからない
        data object ConfigNotFound : ProjectError() {
            override val message: String = "Config file (mpm.json) not found. Run 'mpm init' first."
        }

        // 設定ファイルのパースエラー
        data class ConfigParseError(
            val details: String
        ) : ProjectError() {
            override val message: String = "Failed to parse config: $details"
        }

        // sync依存関係エラー
        data class SyncDependencyError(
            val details: String
        ) : ProjectError() {
            override val message: String = "Sync dependency error: $details"
        }

        // Sync依存関係のバリデーションエラー
        data class SyncValidationFailed(
            val details: String
        ) : ProjectError() {
            override val message: String = "Sync validation failed: $details"
        }

        // 循環依存エラー
        data class CircularDependency(
            val plugins: List<String>
        ) : ProjectError() {
            override val message: String = "Circular dependency detected: ${plugins.joinToString(" -> ")}"
        }

        // 初期化エラー
        data class InitializationFailed(
            val reason: String
        ) : ProjectError() {
            override val message: String = "Project initialization failed: $reason"
        }

        // 保存エラー
        data class SaveFailed(
            val reason: String
        ) : ProjectError() {
            override val message: String = "Failed to save project: $reason"
        }
    }

    // ダウンロード関連のエラー
    sealed class DownloadError : MpmError() {
        // ダウンロード失敗
        data class Failed(
            val url: String,
            val reason: String
        ) : DownloadError() {
            override val message: String = "Download failed from $url: $reason"
        }

        // リポジトリが見つからない
        data class RepositoryNotFound(
            val repoType: String,
            val pluginId: String
        ) : DownloadError() {
            override val message: String = "Repository not found: $repoType:$pluginId"
        }
    }

    // バックアップ関連のエラー
    sealed class BackupError : MpmError() {
        // バックアップ失敗
        data class Failed(
            val reason: String
        ) : BackupError() {
            override val message: String = "Backup failed: $reason"
        }

        // リストア失敗
        data class RestoreFailed(
            val reason: String
        ) : BackupError() {
            override val message: String = "Restore failed: $reason"
        }

        // バックアップが見つからない
        data class NotFound(
            val backupId: String
        ) : BackupError() {
            override val message: String = "Backup not found: $backupId"
        }
    }

    // 汎用エラー
    data class Unknown(
        override val message: String
    ) : MpmError()
}