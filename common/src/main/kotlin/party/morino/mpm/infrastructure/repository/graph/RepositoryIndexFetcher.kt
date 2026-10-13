/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.repository.graph

/**
 * インデックス（index.json）の生テキストを取得する抽象
 *
 * [RepositoryGraphResolver] をHTTP実装から切り離し、テストでは文字列マップで差し替えられるようにする。
 */
fun interface RepositoryIndexFetcher {
    /**
     * 指定URLのインデックスを取得する
     * @param url インデックスの絶対URL
     * @return レスポンス本文。到達不能・非2xx・サイズ超過などの場合はnull
     */
    suspend fun fetch(url: String): String?
}