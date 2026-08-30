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

package party.morino.mpm.api.domain.plugin.scan.model

import java.io.File

/**
 * pluginsディレクトリに実在するプラグインJARを表すモデル
 *
 * plugin.yml / paper-plugin.yml から読み取った情報を保持する。
 * mpm.json（スナップショット）ではなく、ディスク上の実態を表す点に注意。
 *
 * @property file JARファイル
 * @property name plugin.yml / paper-plugin.yml に記載されたプラグイン名
 * @property version plugin.yml / paper-plugin.yml に記載されたバージョン
 */
data class InstalledJar(
    val file: File,
    val name: String,
    val version: String
)