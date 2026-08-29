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

package party.morino.mpm.api.domain.plugin.scan

import party.morino.mpm.api.domain.plugin.scan.model.InstalledJar

/**
 * pluginsディレクトリのJARを走査するスキャナー
 *
 * mpm.jsonのスナップショットではなく「今ディレクトリに何が置かれているか」を返す。
 * 各所に散在していた「pluginsディレクトリのJARを列挙してplugin.ymlから名前を読む」処理を
 * 1箇所に集約するためのコンポーネント。
 */
interface InstalledJarScanner {
    /**
     * pluginsディレクトリ直下のJARを走査する
     *
     * 以下は結果に含めない。
     * - MPM自身のJAR
     * - plugin.yml / paper-plugin.yml を持たない、または解析できないJAR
     * - プラグイン名が空のJAR
     *
     * @return 走査で見つかったプラグインJARの一覧
     */
    fun scan(): List<InstalledJar>
}