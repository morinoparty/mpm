/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.repository.graph

import party.morino.mpm.api.domain.repository.RepositoryFile

/**
 * リポジトリグラフの探索結果
 *
 * @property plugins プラグイン名 -> 定義。祖先側の定義が優先されたあとの最終的なカタログ
 * @property visitedUrls 実際に取得を試みたインデックスURL（探索順）
 * @property warnings 探索中に発生した非致命的な問題（子の取得失敗・制約違反で捨てた定義など）
 */
data class ResolvedRepositoryGraph(
    val plugins: Map<String, RepositoryFile>,
    val visitedUrls: List<String>,
    val warnings: List<String>
)