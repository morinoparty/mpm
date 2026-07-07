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

package party.morino.mpm.api.application.search

import arrow.core.Either
import party.morino.mpm.api.domain.downloader.model.PluginSearchResult
import party.morino.mpm.api.shared.error.MpmError

/**
 * リポジトリ横断プラグイン検索サービス
 *
 * 複数のプラットフォーム（Modrinth/Hangar/SpigotMC/GitHub）を横断してプラグインを検索する。
 */
interface PluginSearchService {
    /**
     * キーワードでプラグインを検索する
     *
     * @param query 検索キーワード
     * @param limit 取得件数の上限
     * @return 検索結果（ダウンロード数の多い順）
     */
    suspend fun search(
        query: String,
        limit: Int
    ): Either<MpmError, List<PluginSearchResult>>
}