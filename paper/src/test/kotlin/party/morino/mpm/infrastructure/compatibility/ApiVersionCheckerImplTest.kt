/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.compatibility

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mpm.api.domain.compatibility.CompatibilityResult

class ApiVersionCheckerImplTest {
    @Test
    @DisplayName("older plugin api-version is compatible with newer server")
    fun testOlderApiVersionIsCompatible() {
        // 1.9 は 1.21 サーバーより古いので互換（サーバー側で読み込めるかは別問題）
        assertEquals(CompatibilityResult.Compatible, ApiVersionCheckerImpl.compareVersions("1.9", "1.21"))
        assertEquals(CompatibilityResult.Compatible, ApiVersionCheckerImpl.compareVersions("1.13", "1.21"))
        assertEquals(CompatibilityResult.Compatible, ApiVersionCheckerImpl.compareVersions("1.21", "1.21"))
    }

    @Test
    @DisplayName("newer plugin api-version than server is incompatible")
    fun testNewerApiVersionIsIncompatible() {
        val result = ApiVersionCheckerImpl.compareVersions("1.21", "1.20")
        assertInstanceOf(CompatibilityResult.Incompatible::class.java, result)
    }

    @Test
    @DisplayName("unparseable api-version is unknown")
    fun testUnparseableIsUnknown() {
        assertInstanceOf(
            CompatibilityResult.Unknown::class.java,
            ApiVersionCheckerImpl.compareVersions("latest", "1.21")
        )
    }
}