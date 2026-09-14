/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

import party.morino.mpm.api.application.model.outdated.OutdatedInfo

/**
 * 更新情報を「現在 → 更新先」の形で表示用に整形する
 *
 * 更新先（[OutdatedInfo.targetVersion]）は mpm.json の指定が指す版であり、固定バージョンでは
 * 上流の最新と一致しない。その場合は「(最新: x)」を添えて、`mpm update` では届かない版が
 * 上流にあることを一目で分かるようにする。コマンド出力とスケジューラのログで共通に使う。
 *
 * @return 例: `1.0.0 → 1.0.1` / `1.0.0 → 1.0.0 (最新: 1.2.0)`
 */
fun OutdatedInfo.describeTransition(): String {
    val base = "$currentVersion → $targetVersion"
    // latest / tag: 指定では target と latest が常に一致するため、注記が付くのは固定バージョンだけ
    return if (hasNewerUpstream) "$base (最新: $latestVersion)" else base
}