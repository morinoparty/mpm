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
 * MpmConfigに対するextension functions
 */

/**
 * pluginsマップをキー（プラグイン名）でアルファベット順（a-Z）にソートした新しいMpmConfigを返す
 * 大文字小文字を区別せずにソートを行う
 *
 * @return pluginsがソートされた新しいMpmConfig
 */
fun MpmConfig.withSortedPlugins(): MpmConfig {
    // キーを大文字小文字を区別せずにソート（a-Z順）
    val sortedPlugins = plugins.toSortedMap(compareBy(String.CASE_INSENSITIVE_ORDER) { it })
    // ソート済みのpluginsマップを持つ新しいMpmConfigを返す
    return this.copy(plugins = sortedPlugins)
}
