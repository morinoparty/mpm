/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.cache

import arrow.core.Either
import party.morino.mpm.api.model.cache.CacheCleanResult
import party.morino.mpm.api.model.cache.CacheEntryInfo
import party.morino.mpm.api.model.cache.CacheSizeInfo
import party.morino.mpm.api.shared.error.MpmError

/**
 * plugins/mpm/cache/ 配下のキャッシュディレクトリを管理するインターフェース
 *
 * `mpm cache list|size|clean` コマンドから利用される（HTTP API には未公開）。
 */
interface CacheManager {
    /**
     * キャッシュエントリの一覧を取得する
     *
     * @return 成功時はCacheEntryInfoのリスト（取得日時の新しい順）、失敗時はMpmError.CacheError
     */
    fun list(): Either<MpmError, List<CacheEntryInfo>>

    /**
     * キャッシュディレクトリ全体のサイズ情報を取得する
     *
     * @return 成功時はCacheSizeInfo、失敗時はMpmError.CacheError
     */
    fun size(): Either<MpmError, CacheSizeInfo>

    /**
     * キャッシュエントリを削除する
     *
     * @param expiredOnly trueの場合はTTLを超過したエントリのみを削除する
     * @return 成功時はCacheCleanResult、失敗時はMpmError.CacheError
     */
    fun clean(expiredOnly: Boolean = false): Either<MpmError, CacheCleanResult>
}