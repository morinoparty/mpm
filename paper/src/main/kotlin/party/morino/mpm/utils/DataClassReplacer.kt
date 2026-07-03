/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils

import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties

/**
 * データクラスのプロパティ値でテンプレート文字列を置換するユーティリティ
 *
 * `<field>` の形式のプレースホルダーをリフレクションで取得したプロパティ値に置換する。
 * ネストしたデータクラスのプロパティは `<field.subField>` のようなドット区切りパスで参照できる。
 */
object DataClassReplacer {
    /**
     * テンプレート文字列中の `<field.path>` プレースホルダーを、対応するプロパティ値で置換する
     * @param dataClass プレースホルダー解決に使用するデータクラスのインスタンス
     * @return 置換後の文字列
     */
    fun String.replaceTemplate(dataClass: Any): String = replaceFields(this, dataClass, "")

    /**
     * dataClassのプロパティを再帰的に走査し、テンプレート内のプレースホルダーを置換する
     * @param template 置換対象のテンプレート文字列
     * @param dataClass プロパティ値の取得元
     * @param prefix ネストしたプロパティを示すドット区切りパスの接頭辞（トップレベルでは空文字）
     */
    private fun replaceFields(
        template: String,
        dataClass: Any,
        prefix: String
    ): String {
        val fields = dataClass::class.memberProperties
        var replaced = template
        fields.forEach { field ->
            // ネストしたプロパティは "親.子" のドット区切りパスで表現する
            val name = if (prefix.isEmpty()) field.name else "$prefix.${field.name}"
            val value = (dataClass::class.members.find { it.name == field.name } as KProperty1<Any, *>).get(dataClass)
            if (isDataClass(value)) {
                // 値自体がデータクラスの場合は、そのプロパティをさらに再帰的に展開する
                replaced = replaceFields(replaced, value!!, name)
            } else {
                // null値は文字列 "null" としてそのまま置換される点に注意
                replaced = replaced.replace("<$name>", value.toString())
            }
        }
        return replaced
    }

    /**
     * 値がデータクラスのインスタンスかどうかを判定する
     * ネストしたプロパティを再帰的に展開すべきかどうかの判断に使用する
     */
    private fun isDataClass(obj: Any?): Boolean {
        if (obj == null) return false
        return obj::class.isData
    }
}