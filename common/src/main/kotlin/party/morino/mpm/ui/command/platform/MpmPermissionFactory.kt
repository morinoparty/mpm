/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.command.platform

import party.morino.mpm.ui.command.annotation.MpmPermission
import revxrsal.commands.Lamp
import revxrsal.commands.annotation.list.AnnotationList
import revxrsal.commands.command.CommandActor
import revxrsal.commands.command.CommandPermission

/**
 * [MpmPermission] を Lamp のパーミッション判定に変換するファクトリー
 *
 * ここで作った判定は Lamp が brigadier の requires にも使うため、
 * 権限の無いサブコマンドはTab補完にも表示されなくなる。
 *
 * @param platform パーミッションを判定するプラットフォーム実装
 */
class MpmPermissionFactory(
    private val platform: CommandPlatform
) : CommandPermission.Factory<CommandActor> {
    /**
     * コマンドのアノテーションからパーミッション判定を作る
     *
     * @param annotations コマンド（クラスから配られたものを含む）のアノテーション
     * @param lamp Lamp のインスタンス
     * @return [MpmPermission] が無ければ null（他のファクトリーに判定を任せる）
     */
    override fun create(
        annotations: AnnotationList,
        lamp: Lamp<CommandActor>
    ): CommandPermission<CommandActor>? {
        // アノテーションが無いコマンドは、このファクトリーの対象外
        val node = annotations.get(MpmPermission::class.java)?.value ?: return null
        return CommandPermission { actor -> platform.hasPermission(actor, node) }
    }
}