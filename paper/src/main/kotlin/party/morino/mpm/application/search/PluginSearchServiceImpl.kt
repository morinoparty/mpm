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

package party.morino.mpm.application.search

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.application.search.PluginSearchService
import party.morino.mpm.api.domain.downloader.DownloaderRepository
import party.morino.mpm.api.domain.downloader.model.PluginSearchResult
import party.morino.mpm.api.shared.error.MpmError

/**
 * [PluginSearchService] の実装
 *
 * [DownloaderRepository] の横断検索へ委譲する薄いファサード。
 */
class PluginSearchServiceImpl :
    PluginSearchService,
    KoinComponent {
    // Koinによる依存性注入
    private val downloaderRepository: DownloaderRepository by inject()

    override suspend fun search(
        query: String,
        limit: Int
    ): Either<MpmError, List<PluginSearchResult>> =
        try {
            downloaderRepository.searchPlugins(query, limit).right()
        } catch (e: Exception) {
            // 全プラットフォームの検索が失敗した場合のみここに到達する（個別失敗は空リスト扱い）
            MpmError.Unknown("検索に失敗しました: ${e.message}").left()
        }
}