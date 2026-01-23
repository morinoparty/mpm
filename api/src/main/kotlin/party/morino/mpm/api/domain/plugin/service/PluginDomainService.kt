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

package party.morino.mpm.api.domain.plugin.service

import arrow.core.Either
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.domain.plugin.model.PluginSpec
import party.morino.mpm.api.domain.plugin.model.VersionDetail
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.shared.error.MpmError

/**
 * プラグインに関するドメインサービス
 *
 * 複数のエンティティをまたぐドメインロジックや、
 * 外部API呼び出しが必要なロジックを集約する
 */
interface PluginDomainService {
    /**
     * バージョンを解決する
     *
     * VersionSpecifierに基づいて、実際のバージョン詳細を取得する
     *
     * @param name プラグイン名（例: "modrinth:bluemap"）
     * @param specifier バージョン指定
     * @return 解決されたバージョン詳細
     */
    suspend fun resolveVersion(
        name: PluginName,
        specifier: VersionSpecifier
    ): Either<MpmError, VersionDetail>

    /**
     * 依存関係を解決する
     *
     * プラグインの依存関係を再帰的に解決し、必要なプラグイン一覧を返す
     *
     * @param spec プラグイン指定
     * @return 必要なプラグイン一覧（依存先を含む）
     */
    suspend fun resolveDependencies(spec: PluginSpec): Either<MpmError, List<PluginSpec>>

    /**
     * プラグインの利用可能なバージョン一覧を取得する
     *
     * @param name プラグイン名
     * @return バージョン一覧
     */
    suspend fun getAvailableVersions(name: PluginName): Either<MpmError, List<VersionDetail>>
}