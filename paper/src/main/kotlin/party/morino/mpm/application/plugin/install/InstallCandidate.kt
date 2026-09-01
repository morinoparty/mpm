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

package party.morino.mpm.application.plugin.install

/**
 * 一括インストールの計画を立てるために必要な、プラグイン1件分の入力
 *
 * ディスクI/O（メタデータの読み込み）は呼び出し側で済ませ、計画そのものは
 * 純粋関数（[planInstallTargets]）で決められるようにするための入力DTO。
 *
 * @param pluginName プラグイン名
 * @param expectedVersion mpm.json に書かれたバージョン指定（`1.0.0` / `latest` / `tag:beta` / `sync:Parent`）
 * @param installedVersion メタデータに記録された現在のバージョン（raw）。
 *   メタデータが存在しない、または読み込めない場合はnull（＝インストールが必要）
 * @param locked メタデータの `settings.lock` が true か。
 *   メタデータを読めなかった場合はロック状態を判定できないため false とする
 */
data class InstallCandidate(
    val pluginName: String,
    val expectedVersion: String,
    val installedVersion: String?,
    val locked: Boolean
)