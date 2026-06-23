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

package party.morino.mpm.api.application.model

/**
 * unmanagedプラグインのadopt結果
 *
 * すべてのunmanagedプラグインをリポジトリから検索し、
 * 見つかったものをmanaged状態に変更した結果を表す
 *
 * @property adoptedPlugins 正常にadoptされたプラグインのリスト（依存関係含む）
 * @property skippedPlugins リポジトリに見つからなかったプラグイン名のリスト
 * @property failedPlugins adopt中にエラーが発生したプラグインとエラーメッセージのマップ
 * @property notFoundDependencies 依存関係として必要だがリポジトリに見つからなかったプラグイン名のリスト
 * @property versionMismatchPlugins --pin指定時にバージョンが一致せずスキップされたプラグインと理由のマップ
 */
data class AdoptResult(
    val adoptedPlugins: List<PluginAddResult>,
    val skippedPlugins: List<String>,
    val failedPlugins: Map<String, String>,
    val notFoundDependencies: List<String>,
    val pinnedPlugins: List<String> = emptyList(),
    val hashMismatchWarnings: Map<String, String> = emptyMap(),
    val versionMismatchPlugins: Map<String, String> = emptyMap()
) {
    /**
     * すべてのプラグインが正常にadoptされたかどうか
     */
    val isSuccess: Boolean
        get() = failedPlugins.isEmpty()

    /**
     * adoptされたプラグインの総数
     */
    val totalAdopted: Int
        get() = adoptedPlugins.size
}