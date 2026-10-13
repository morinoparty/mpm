/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.command.platform

import net.kyori.adventure.audience.Audience
import revxrsal.commands.command.CommandActor
import revxrsal.commands.command.CommandParameter
import revxrsal.commands.command.ExecutableCommand
import revxrsal.commands.process.SenderResolver

/**
 * 型が [Audience] のパラメータを、コマンドの送信者として解決する [SenderResolver]
 *
 * 共通のコマンドクラスは送信者を [Audience] で受け取るため、Lamp に送信者の型として教える必要がある。
 *
 * @param platform 実行者から [Audience] を取り出すプラットフォーム実装
 */
class AudienceSenderResolver(
    private val platform: CommandPlatform
) : SenderResolver<CommandActor> {
    /**
     * パラメータが送信者を受け取るものかを判定する
     *
     * Player や CommandSender も [Audience] を実装しているため、継承関係ではなく型の一致で判定する。
     * こうしておくと、プラットフォーム固有の型のパラメータは各プラットフォームの Resolver がそのまま扱う。
     *
     * @param parameter 判定するパラメータ
     * @return 型がちょうど [Audience] なら true
     */
    override fun isSenderType(parameter: CommandParameter): Boolean = parameter.type() == Audience::class.java

    /**
     * 実行者に対応する [Audience] を返す
     *
     * @param customSenderType パラメータの型（常に [Audience]）
     * @param actor Lamp の実行者
     * @param command 実行するコマンド
     * @return 実行者に対応する [Audience]
     */
    override fun getSender(
        customSenderType: Class<*>,
        actor: CommandActor,
        command: ExecutableCommand<CommandActor>
    ): Any = platform.audienceOf(actor)
}