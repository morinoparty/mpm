/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
 * and related and neighboring rights to this software to the public domain worldwide.
 * This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.project.service

import arrow.core.Either
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.shared.error.MpmError

/**
 * プロジェクトに関するドメインサービス
 *
 * MpmProjectに関するドメインロジックを集約する
 */
interface ProjectDomainService {
    /**
     * バージョン指定文字列をパースする
     *
     * mpm.jsonのバージョン文字列をVersionSpecifierに変換する
     *
     * 例:
     * - "1.0.0" → VersionSpecifier.Fixed("1.0.0")
     * - "latest" → VersionSpecifier.Latest
     * - "sync:QuickShop" → VersionSpecifier.Sync("QuickShop")
     * - "tag:stable" → VersionSpecifier.Tag("stable")
     * - "pattern:^5\\.4\\..*" → VersionSpecifier.Pattern("^5\\.4\\..*")
     *
     * @param input バージョン文字列
     * @return パースされたVersionSpecifier
     */
    fun parseVersionSpecifier(input: String): Either<MpmError, VersionSpecifier>
}