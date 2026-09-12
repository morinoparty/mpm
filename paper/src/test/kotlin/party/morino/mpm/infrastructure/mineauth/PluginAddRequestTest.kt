/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.domain.plugin.model.VersionSpecifierParser
import party.morino.mpm.infrastructure.mineauth.model.lifecycle.PluginAddRequest

/**
 * プラグイン追加リクエストボディの解釈を検証するテスト
 *
 * ハンドラーは受け取った version をそのまま [VersionSpecifierParser] に渡すため、
 * 既定値とパーサーへの受け渡しだけを対象とする。
 */
@DisplayName("Plugin add request body")
class PluginAddRequestTest {
    @Test
    @DisplayName("empty body defaults the version to latest")
    fun emptyBodyDefaultsToLatest() {
        // version を省略した `{}` でも「最新版を追加する」という一番多い用途が通ること
        val request = Json.decodeFromString<PluginAddRequest>("{}")
        assertEquals("latest", request.version)
    }

    @Test
    @DisplayName("sync spec reaches the parser as a sync specifier")
    fun syncSpecIsParsedIntoSyncSpecifier() {
        val request = Json.decodeFromString<PluginAddRequest>("""{"version": "sync:MineAuth"}""")
        assertEquals(VersionSpecifier.Sync("MineAuth"), VersionSpecifierParser.parse(request.version))
    }
}