/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.mock.platform

import party.morino.mpm.platform.SelfInfo
import java.io.File

/**
 * テスト用の [SelfInfo]
 *
 * JARから読み込まれていない（開発環境と同じ）状態を模すため、[jar] は null を返す。
 *
 * @property id mpm 自身のプラグイン名
 * @property version 実行中の mpm のバージョン
 */
class TestSelfInfo(
    override val id: String = "mpm",
    override val version: String = "0.0.0-test"
) : SelfInfo {
    override val jar: File? = null
}