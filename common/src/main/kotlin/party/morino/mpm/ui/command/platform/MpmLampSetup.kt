/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.command.platform

import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import party.morino.mpm.utils.command.resolver.InstalledPluginParameterType
import party.morino.mpm.utils.command.resolver.RepositoryPluginParameterType
import party.morino.mpm.utils.command.resolver.VersionSpecifierParameterType
import revxrsal.commands.Lamp
import revxrsal.commands.LampBuilderVisitor
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.dynamic.Annotations
import revxrsal.commands.command.CommandActor

/**
 * Paper / Velocity の Lamp に共通の設定を加える [LampBuilderVisitor]
 *
 * 送信者の解決（[AudienceSenderResolver]）、[party.morino.mpm.ui.command.annotation.MpmPermission] の判定、
 * mpm 独自のパラメータ型の登録を行う。ルート名が `mpm` 以外のプラットフォーム（Velocity）では、
 * コマンドクラスの `@Command("mpm ...")` を実行時にそのルート名へ書き換える。
 *
 * 各プラットフォームの設定（BukkitLamp / VelocityLamp）の後に適用すること。
 *
 * @param platform コマンドの実行者を解釈するプラットフォーム実装
 */
class MpmLampSetup(
    private val platform: CommandPlatform
) : LampBuilderVisitor<CommandActor> {
    /**
     * Lamp のビルダーに mpm 共通の設定を加える
     *
     * @param builder Lamp のビルダー
     */
    override fun visit(builder: Lamp.Builder<CommandActor>) {
        // 送信者を Audience として受け取れるようにする
        builder.senderResolver(AudienceSenderResolver(platform))
        // MpmPermission をプラットフォームのパーミッション判定に変換する
        builder.permissionFactory(MpmPermissionFactory(platform))
        // mpm 独自のパラメータ型（補完と検証を行う）を登録する
        builder.parameterTypes { types ->
            types.addParameterType(RepositoryPlugin::class.java, RepositoryPluginParameterType())
            types.addParameterType(InstalledPlugin::class.java, InstalledPluginParameterType())
            types.addParameterType(VersionSpecifier::class.java, VersionSpecifierParameterType())
        }
        // ルート名が既定と同じなら書き換えは不要
        if (platform.rootLabel == DEFAULT_ROOT) return
        builder.annotationReplacer(Command::class.java) { _, command ->
            // @Command は複数のパス（例: "mpm", "mpm backup"）を持てるため、すべて書き換える
            val renamed = command.value.map { path -> renameRoot(path, platform.rootLabel) }.toTypedArray()
            listOf(Annotations.create(Command::class.java, "value", renamed))
        }
    }

    companion object {
        /** コマンドクラスに書かれているルート名 */
        const val DEFAULT_ROOT = "mpm"

        /**
         * コマンドパスの先頭が既定のルート名なら、指定したルート名に置き換える
         *
         * @param path コマンドパス（例: `mpm backup`）
         * @param root 置き換え後のルート名
         * @return 置き換えたコマンドパス（先頭が既定のルート名でなければそのまま）
         */
        fun renameRoot(
            path: String,
            root: String
        ): String {
            // 先頭の単語だけを比較し、"mpmx" のような別名を誤って書き換えないようにする
            val parts = path.split(' ', limit = 2)
            if (parts[0] != DEFAULT_ROOT) return path
            return (listOf(root) + parts.drop(1)).joinToString(" ")
        }
    }
}