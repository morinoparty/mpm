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

package party.morino.mpm.api.application.model.add

/**
 * 依存関係を含めたプラグイン追加の結果
 *
 * @property addedPlugins 追加・インストールされたプラグインのリスト（インストール結果を含む）
 * @property skippedPlugins 既に追加済みのためスキップされたプラグイン名のリスト
 * @property failedPlugins 追加に失敗したプラグインとエラーメッセージのマップ
 * @property notFoundPlugins リポジトリに見つからなかった依存プラグイン名のリスト
 */
data class AddWithDependenciesResult(
    val addedPlugins: List<PluginAddResult>,
    val skippedPlugins: List<String>,
    val failedPlugins: Map<String, String>,
    val notFoundPlugins: List<String>
) {
    /**
     * すべてのプラグインが正常に追加されたかどうか
     */
    val isSuccess: Boolean
        get() = failedPlugins.isEmpty() && notFoundPlugins.isEmpty()

    /**
     * 追加されたプラグインの総数
     */
    val totalAdded: Int
        get() = addedPlugins.size
}