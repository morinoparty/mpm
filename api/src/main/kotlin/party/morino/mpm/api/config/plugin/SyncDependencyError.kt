/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related
 * and neighboring rights to this software to the public domain worldwide.
 * This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.config.plugin

/**
 * Sync依存関係に関するエラーを表すsealed class
 *
 * バージョン同期機能で発生する可能性のあるエラーを型安全に表現する
 */
sealed class SyncDependencyError {
    /**
     * エラーメッセージを生成する抽象メソッド
     */
    abstract fun toMessage(): String
    /**
     * 循環依存エラー
     *
     * A → B → C → A のように、同期依存関係が循環している場合に発生
     *
     * @property cycle 循環しているプラグイン名のリスト
     */
    data class CircularDependency(val cycle: List<String>) : SyncDependencyError() {
        /**
         * エラーメッセージを生成する
         */
        override fun toMessage(): String =
            "循環依存が検出されました: ${cycle.joinToString(" → ")}"
    }

    /**
     * ターゲットプラグイン未発見エラー
     *
     * sync:PluginName で指定されたプラグインがmpm.jsonに存在しない場合に発生
     *
     * @property pluginName 同期を設定したプラグイン名
     * @property targetPlugin 見つからなかったターゲットプラグイン名
     */
    data class TargetNotFound(
        val pluginName: String,
        val targetPlugin: String
    ) : SyncDependencyError() {
        /**
         * エラーメッセージを生成する
         */
        override fun toMessage(): String =
            "プラグイン '$pluginName' が同期対象とする '$targetPlugin' が見つかりません"
    }

    /**
     * バージョン未対応エラー
     *
     * ターゲットプラグインのバージョンがアドオンプラグインで利用できない場合に発生
     *
     * @property pluginName 同期を設定したプラグイン名
     * @property targetPlugin ターゲットプラグイン名
     * @property targetVersion ターゲットのバージョン
     * @property availableVersions 利用可能なバージョンのリスト
     */
    data class VersionNotAvailable(
        val pluginName: String,
        val targetPlugin: String,
        val targetVersion: String,
        val availableVersions: List<String>
    ) : SyncDependencyError() {
        /**
         * エラーメッセージを生成する
         */
        override fun toMessage(): String {
            val availableStr = if (availableVersions.isEmpty()) {
                "利用可能なバージョンがありません"
            } else {
                "利用可能なバージョン: ${availableVersions.take(5).joinToString(", ")}"
            }
            return "プラグイン '$pluginName' に '$targetPlugin' のバージョン '$targetVersion' が存在しません。$availableStr"
        }
    }

    /**
     * ターゲットがSync指定エラー
     *
     * 同期対象のプラグインもSync指定になっている場合に発生
     * （循環依存の一種だが、直接的なエラーとして扱う）
     *
     * @property pluginName 同期を設定したプラグイン名
     * @property targetPlugin 同期対象のプラグイン名
     * @property targetSyncPlugin ターゲットが同期しているプラグイン名
     */
    data class TargetIsSync(
        val pluginName: String,
        val targetPlugin: String,
        val targetSyncPlugin: String
    ) : SyncDependencyError() {
        /**
         * エラーメッセージを生成する
         */
        override fun toMessage(): String =
            "プラグイン '$pluginName' の同期対象 '$targetPlugin' も同期設定（sync:$targetSyncPlugin）になっています"
    }

    /**
     * ターゲットがunmanagedエラー
     *
     * 同期対象のプラグインが手動管理（unmanaged）になっている場合に発生
     *
     * @property pluginName 同期を設定したプラグイン名
     * @property targetPlugin 同期対象のプラグイン名
     */
    data class TargetIsUnmanaged(
        val pluginName: String,
        val targetPlugin: String
    ) : SyncDependencyError() {
        /**
         * エラーメッセージを生成する
         */
        override fun toMessage(): String =
            "プラグイン '$pluginName' の同期対象 '$targetPlugin' は手動管理（unmanaged）です"
    }
}
