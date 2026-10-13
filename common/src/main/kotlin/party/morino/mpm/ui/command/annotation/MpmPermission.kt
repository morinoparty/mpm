/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.command.annotation

import revxrsal.commands.annotation.DistributeOnMethods

/**
 * mpm のコマンドを実行するために必要なパーミッションノード
 *
 * Lamp の `CommandPermission` はプラットフォームごとに別のアノテーション（Bukkit 用・Velocity 用）になっているため、
 * Paper と Velocity で同じコマンドクラスを使えるよう、共通のアノテーションを用意する。
 * 実際の判定は各プラットフォームの [party.morino.mpm.ui.command.platform.CommandPlatform] が行う。
 *
 * クラスに付けた場合はクラス内のすべてのサブコマンドに適用され、メソッドに付けた場合はそちらが優先される。
 *
 * @property value パーミッションノード（例: `mpm.command.list`）
 */
// DistributeOnMethods が無いと、クラスに付けたアノテーションがメソッドへ配られず、誰でも実行できてしまう
@DistributeOnMethods
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MpmPermission(
    val value: String
)