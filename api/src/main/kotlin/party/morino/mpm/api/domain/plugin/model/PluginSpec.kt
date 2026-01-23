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

package party.morino.mpm.api.domain.plugin.model

/**
 * mpm.json内のプラグイン指定を表すsealed class
 *
 * mpm.jsonではプラグイン名に対して以下の指定が可能：
 * - "unmanaged": MPMで管理しない（手動インストール）
 * - バージョン文字列: VersionSpecifierとしてパースされる
 */
sealed class PluginSpec {
    // プラグイン名を取得
    abstract val name: PluginName

    /**
     * MPMで管理するプラグイン
     *
     * @property name プラグイン名
     * @property versionRequirement バージョン指定
     */
    data class Managed(
        override val name: PluginName,
        val versionRequirement: VersionSpecifier
    ) : PluginSpec()

    /**
     * MPMで管理しないプラグイン
     *
     * pluginsディレクトリに存在するが、MPMによる更新対象外のプラグイン
     *
     * @property name プラグイン名
     */
    data class Unmanaged(
        override val name: PluginName
    ) : PluginSpec()
}