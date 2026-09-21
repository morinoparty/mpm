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
 * 親リポジトリは、子が「どの名前のプラグインを」「どの配布元から」定義してよいかを
 * このリンクで宣言する。子から受け取った定義のうち、この宣言に収まらないものは
 * クライアント側で黙って捨てられる（親が子の権限を絞る信頼境界）。
 *
 * 子がさらに孫へリンクを張る場合、孫の定義には経路上のすべてのリンクの制約が
 * 同時に適用される（子は親から与えられた権限を広げられない）。
 *
 * @property index 子リポジトリのインデックス（index.json）の絶対URL。
 *   https 限定で、IPリテラル・localhost・.local ドメインは拒否される
 * @property scope 子が定義してよいプラグイン名のパターン。
 *   完全一致、または末尾 `*` のみをプレフィックス一致のワイルドカードとして扱う。
 *   例: `["MineAuth", "MineAuth-*"]`
 * @property allowedSources 子が repositories[] に書いてよい配布元のallowlist。
 *   `type:id` 形式（例: `github:morinoparty/MineAuth`, `modrinth:9LoU3yUC`）。
 *   末尾 `*` のみワイルドカード。大文字小文字は区別しない
 */
@Serializable
data class RepositoryLink(
    val index: String,
    val scope: List<String> = emptyList(),
    val allowedSources: List<String> = emptyList()
)