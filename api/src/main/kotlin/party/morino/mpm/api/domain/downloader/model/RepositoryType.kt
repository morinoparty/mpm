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

package party.morino.mpm.api.domain.downloader.model

/**
 * プラグインのリポジトリタイプを表す列挙型
 * @property url 各リポジトリのベースURL
 */
enum class RepositoryType(
    val url: String
) {
    GITHUB("https://github.com"),
    SPIGOTMC("https://www.spigotmc.org"),
    HANGER("https://hangar.papermc.io"),
    MODRINTH("https://modrinth.com")
}