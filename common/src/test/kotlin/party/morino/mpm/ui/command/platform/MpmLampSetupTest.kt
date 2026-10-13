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
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.ui.command.MpmCommands
import party.morino.mpm.ui.command.annotation.MpmPermission
import party.morino.mpm.utils.command.sendRichMessage
import revxrsal.commands.Lamp
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.command.CommandActor
import java.util.UUID

class MpmLampSetupTest {
    /** テスト用の実行者（Lamp の CommandActor とメッセージの送信先を兼ねる） */
    class TestActor(
        private val permissions: Set<String>
    ) : CommandActor,
        Audience {
        // 受け取ったメッセージ（MiniMessage を解釈した後のプレーンテキスト）
        val messages = mutableListOf<String>()
        lateinit var lampInstance: Lamp<*>

        fun has(node: String): Boolean = node in permissions

        override fun name(): String = "test"

        override fun uniqueId(): UUID = UUID(0, 0)

        override fun sendRawMessage(message: String) {
            messages.add(message)
        }

        override fun sendRawError(message: String) {
            messages.add(message)
        }

        override fun lamp(): Lamp<*> = lampInstance

        override fun sendMessage(message: Component) {
            messages.add(PlainTextComponentSerializer.plainText().serialize(message))
        }
    }

    /** テスト用のプラットフォーム実装 */
    class TestPlatform(
        override val rootLabel: String
    ) : CommandPlatform {
        override fun audienceOf(actor: CommandActor): Audience = actor as TestActor

        override fun hasPermission(
            actor: CommandActor,
            node: String
        ): Boolean = (actor as TestActor).has(node)
    }

    /** クラスに付けたパーミッションがメソッドへ配られるかを確かめるためのコマンド */
    @Command("mpm")
    @MpmPermission("mpm.command.test")
    class GuardedCommand {
        @Subcommand("ping")
        fun ping(sender: Audience) {
            sender.sendRichMessage("<green>pong")
        }
    }

    // 指定したルート名で mpm の設定を適用した Lamp を作る
    private fun lampFor(root: String): Lamp<CommandActor> =
        Lamp.builder<CommandActor>().accept(MpmLampSetup(TestPlatform(root))).build()

    @Test
    @DisplayName("rewrites every mpm command root to mpmv and keeps permissions")
    fun rewritesRootAndGuardsEveryCommand() {
        val lamp = lampFor("mpmv")
        // コマンドの生成・登録だけなら Koin は不要（依存は実行時に遅延取得される）
        lamp.register(*MpmCommands.all().toTypedArray())
        val nobody = TestActor(emptySet()).also { it.lampInstance = lamp }

        val commands = lamp.registry().toList()
        assertTrue(commands.isNotEmpty())
        commands.forEach { command ->
            // すべてのコマンドが /mpmv の下に登録される
            assertEquals("mpmv", command.path().split(' ').first(), command.path())
            // すべてのコマンドクラスに MpmPermission があるため、権限の無い実行者は実行できない
            assertFalse(command.permission().isExecutableBy(nobody), command.path())
        }
    }

    @Test
    @DisplayName("applies class-level permission and resolves Audience sender")
    fun classLevelPermissionAndAudienceSender() {
        val lamp = lampFor("mpm")
        lamp.register(GuardedCommand())
        val allowed = TestActor(setOf("mpm.command.test")).also { it.lampInstance = lamp }
        val denied = TestActor(emptySet()).also { it.lampInstance = lamp }

        lamp.dispatch(allowed, "mpm ping")
        lamp.dispatch(denied, "mpm ping")

        // 権限がある実行者には Audience 経由で MiniMessage のメッセージが届く
        assertEquals(listOf("pong"), allowed.messages)
        // 権限が無い実行者にはコマンドが実行されない
        assertFalse(denied.messages.contains("pong"))
    }

    @Test
    @DisplayName("renames only an exact mpm root word")
    fun renameRootOnlyMatchesWholeWord() {
        assertEquals("mpmv backup", MpmLampSetup.renameRoot("mpm backup", "mpmv"))
        assertEquals("mpmv", MpmLampSetup.renameRoot("mpm", "mpmv"))
        assertEquals("mpmx list", MpmLampSetup.renameRoot("mpmx list", "mpmv"))
    }
}