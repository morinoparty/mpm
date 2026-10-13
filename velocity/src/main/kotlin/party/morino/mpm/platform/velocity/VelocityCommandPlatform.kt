/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.platform.velocity

import com.velocitypowered.api.permission.PermissionSubject
import com.velocitypowered.api.permission.Tristate
import net.kyori.adventure.audience.Audience
import party.morino.mpm.api.domain.downloader.model.TargetPlatform
import party.morino.mpm.ui.command.platform.CommandPlatform
import revxrsal.commands.command.CommandActor
import revxrsal.commands.velocity.actor.VelocityCommandActor

/**
 * Velocity 用の [CommandPlatform] 実装
 *
 * プロキシの `/mpm` はバックエンドの `/mpm` を隠してしまうため、ルートは `/mpmv` にする。
 * Velocity には Bukkit のようなパーミッション階層が無いため、親ノードを順にたどって判定する（[hasPermission]）。
 */
class VelocityCommandPlatform : CommandPlatform {
    /** バックエンドの `/mpm` と衝突しないルート名 */
    override val rootLabel: String = TargetPlatform.VELOCITY.commandRoot

    /**
     * Lamp の実行者から Velocity の CommandSource を取り出す
     *
     * @param actor Lamp の実行者（VelocityLamp では常に [VelocityCommandActor]）
     * @return 実行者の CommandSource
     */
    override fun audienceOf(actor: CommandActor): Audience = (actor as VelocityCommandActor).source()

    /**
     * Velocity のパーミッションで判定する
     *
     * @param actor Lamp の実行者
     * @param node パーミッションノード
     * @return 持っていれば true
     */
    override fun hasPermission(
        actor: CommandActor,
        node: String
    ): Boolean = hasPermission((actor as VelocityCommandActor).source(), node)

    companion object {
        /** 親ノードとしてたどる最も短いノードの区切り数（`mpm.command` まで。`mpm` 単体は見ない） */
        private const val MIN_PARENT_SEGMENTS = 2

        /**
         * ノード自身から親ノードへ順にたどり、最初に値が設定されているもので判定する
         *
         * Paper では `mpm.command` が `mpm.command.add` などを子に持つため、`mpm.command` だけを与えれば全コマンドを使える。
         * Velocity でも同じ与え方ができるよう、`mpm.command.add` が未設定なら `mpm.command` を見る。
         * 明示的に false が設定されたノードはそこで拒否する（親が true でも上書きしない）。
         *
         * @param subject 判定する対象（プレイヤーやコンソール）
         * @param node パーミッションノード
         * @return 持っていれば true（どのノードにも値が無ければ false）
         */
        fun hasPermission(
            subject: PermissionSubject,
            node: String
        ): Boolean {
            val segments = node.split('.')
            // 長いノードから順に見て、最初に値が決まっているものを採用する
            for (size in segments.size downTo minOf(MIN_PARENT_SEGMENTS, segments.size)) {
                when (subject.getPermissionValue(segments.take(size).joinToString("."))) {
                    Tristate.TRUE -> return true
                    Tristate.FALSE -> return false
                    // 未設定なら親ノードへ進む
                    Tristate.UNDEFINED -> continue
                }
            }
            return false
        }
    }
}