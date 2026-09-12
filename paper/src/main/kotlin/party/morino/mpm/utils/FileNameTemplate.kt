/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils

/**
 * ダウンロードしたJARのファイル名テンプレートを展開する
 *
 * 以前は kotlin-reflect でデータクラスのプロパティを走査して置換していたが、
 * mpm 自身の更新で実行中のJARがディスクから消えると kotlin-reflect が
 * `kotlin/kotlin.builtins` リソースを読み直せず AssertionError で止まる問題があった。
 * 置換対象は2つしかないため、リフレクションを使わず単純な文字列置換で展開する。
 */
internal object FileNameTemplate {
    /** プラグイン名に置換されるプレースホルダー */
    const val PLUGIN_NAME = "<pluginInfo.name>"

    /** 正規化済みバージョンに置換されるプレースホルダー */
    const val NORMALIZED_VERSION = "<mpmInfo.version.current.normalized>"

    /** テンプレートが指定されていない場合の既定値 */
    const val DEFAULT = "$PLUGIN_NAME-$NORMALIZED_VERSION.jar"

    /**
     * テンプレートを展開してファイル名を生成する
     *
     * @param template ファイル名テンプレート（null の場合は [DEFAULT]）
     * @param pluginName プラグイン名
     * @param normalizedVersion 正規化済みバージョン文字列
     * @return 展開後のファイル名
     */
    fun render(
        template: String?,
        pluginName: String,
        normalizedVersion: String
    ): String =
        (template ?: DEFAULT)
            .replace(PLUGIN_NAME, pluginName)
            .replace(NORMALIZED_VERSION, normalizedVersion)
}