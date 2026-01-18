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

package party.morino.mpm.api.model.dependency

/**
 * 依存関係解析時のエラーを表すsealed class
 */
sealed class DependencyError {
    /**
     * 指定されたプラグインが見つからないエラー
     * @property pluginName 見つからなかったプラグイン名
     */
    data class PluginNotFound(
        val pluginName: String
    ) : DependencyError() {
        override fun toString(): String = "プラグインが見つかりません: $pluginName"
    }

    /**
     * 必須依存プラグインが不足しているエラー
     * @property pluginName 対象プラグイン名
     * @property missingDependencies 不足している依存プラグインのリスト
     */
    data class MissingRequiredDependency(
        val pluginName: String,
        val missingDependencies: List<String>
    ) : DependencyError() {
        override fun toString(): String = "$pluginName の必須依存プラグインが不足しています: ${missingDependencies.joinToString(", ")}"
    }

    /**
     * 循環依存が検出されたエラー
     * @property cycle 循環しているプラグイン名のリスト
     */
    data class CircularDependency(
        val cycle: List<String>
    ) : DependencyError() {
        override fun toString(): String = "循環依存が検出されました: ${cycle.joinToString(" -> ")}"
    }

    /**
     * プラグインの読み込みに失敗したエラー
     * @property pluginName プラグイン名
     * @property reason 失敗理由
     */
    data class PluginLoadError(
        val pluginName: String,
        val reason: String
    ) : DependencyError() {
        override fun toString(): String = "$pluginName の読み込みに失敗しました: $reason"
    }

    /**
     * 逆依存が存在するエラー（アンインストール時）
     * @property pluginName 削除しようとしているプラグイン名
     * @property dependents このプラグインに依存しているプラグインのリスト
     */
    data class HasDependents(
        val pluginName: String,
        val dependents: List<String>
    ) : DependencyError() {
        override fun toString(): String = "$pluginName は以下のプラグインから依存されています: ${dependents.joinToString(", ")}"
    }
}