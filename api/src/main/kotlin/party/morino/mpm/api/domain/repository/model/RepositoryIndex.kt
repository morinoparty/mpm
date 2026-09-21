/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.repository.model

import kotlinx.serialization.Serializable
import party.morino.mpm.api.domain.repository.RepositoryFile

/**
 * リモートリポジトリが配信するインデックス（index.json）のデータ構造
 *
 * リポジトリは静的ファイル1つ（index.json）で表現され、そのリポジトリが定義する
 * 全プラグインと、子リポジトリへのリンクを含む。クライアントはルートのindexから
 * [children] を再帰的にたどってプラグインカタログを組み立てる
 * （グラフ探索の仕様は RepositoryGraphResolver を参照）。
 *
 * ルートも子も同じ形式なので、どのリポジトリも他のリポジトリの子になれる。
 *
 * @property schemaVersion インデックス形式の版数
 * @property name リポジトリの表示名（任意、ログ・デバッグ用）
 * @property generated 生成時刻（任意、デバッグ用）
 * @property plugins プラグイン名 -> 定義。キーと定義の id は一致していなければならない
 * @property children 子リポジトリへのリンク（任意）
 */
@Serializable
data class RepositoryIndex(
    val schemaVersion: Int = 1,
    val name: String? = null,
    val generated: String? = null,
    val plugins: Map<String, RepositoryFile> = emptyMap(),
    val children: List<RepositoryLink> = emptyList()
)