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

/**
 * リポジトリインデックスから子リポジトリへ張るリンク（グラフの辺）
 *
 * リンクは基本的に URL だけで成り立ち、子はどんな名前のプラグインでも定義できる
 * （同名は祖先側が勝つため、親の定義を上書きすることはできない）。
 * リポジトリ同士をチェーンのようにつないでカタログを広げていく用途を想定している。
 *
 * 必要な場合だけ [allowedSources] で子が名乗れる配布元を絞れる。
 * 指定した場合、子がさらに孫へリンクを張っても、孫の定義には経路上のすべての
 * 制約が同時に適用される（子は親から与えられた権限を広げられない）。
 *
 * @property index 子リポジトリのインデックス（index.json）の絶対URL。
 *   https 限定で、IPリテラル・localhost・.local ドメインは拒否される
 * @property allowedSources 子が repositories[] に書いてよい配布元のallowlist（任意）。
 *   空なら無制限。`type:id` 形式（例: `github:morinoparty/MineAuth`, `modrinth:9LoU3yUC`）。
 *   末尾 `*` のみワイルドカード。大文字小文字は区別しない
 */
@Serializable
data class RepositoryLink(
    val index: String,
    val allowedSources: List<String> = emptyList()
)