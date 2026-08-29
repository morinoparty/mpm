/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mineauth.api.http.HttpStatus
import party.morino.mpm.api.shared.error.MpmError

/**
 * MpmError から HTTP ステータスへのマッピングを検証する
 *
 * 404 / 409 / 400 / 500 の代表的な分岐のみを対象とする。
 */
@DisplayName("MpmError to HttpStatus mapping")
class MpmErrorHttpMappingTest {
    @Test
    @DisplayName("missing resources map to 404")
    fun notFoundErrors() {
        assertEquals(HttpStatus.NOT_FOUND, MpmError.PluginError.NotFound("a").toHttpStatus())
        assertEquals(HttpStatus.NOT_FOUND, MpmError.PluginError.NotManaged("a").toHttpStatus())
        assertEquals(HttpStatus.NOT_FOUND, MpmError.ProjectError.ConfigNotFound.toHttpStatus())
    }

    @Test
    @DisplayName("state conflicts map to 409")
    fun conflictErrors() {
        assertEquals(HttpStatus.CONFLICT, MpmError.PluginError.Locked("a").toHttpStatus())
        assertEquals(HttpStatus.CONFLICT, MpmError.PluginError.NotLocked("a").toHttpStatus())
        assertEquals(HttpStatus.CONFLICT, MpmError.PluginError.UpdateInProgress.toHttpStatus())
        assertEquals(HttpStatus.CONFLICT, MpmError.PluginError.AlreadyExists("a").toHttpStatus())
        assertEquals(
            HttpStatus.CONFLICT,
            MpmError.PluginError.VersionSwitchNotAllowed("a", "sync").toHttpStatus()
        )
    }

    @Test
    @DisplayName("unresolvable input maps to 400")
    fun badRequestErrors() {
        assertEquals(
            HttpStatus.BAD_REQUEST,
            MpmError.PluginError.VersionResolutionFailed("a", "no such version").toHttpStatus()
        )
    }

    @Test
    @DisplayName("server side failures map to 500")
    fun internalErrors() {
        assertEquals(
            HttpStatus.INTERNAL_SERVER_ERROR,
            MpmError.PluginError.UpdateFailed("a", "io").toHttpStatus()
        )
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, MpmError.Unknown("boom").toHttpStatus())
    }
}