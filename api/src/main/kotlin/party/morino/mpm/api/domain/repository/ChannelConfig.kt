/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.repository

import kotlinx.serialization.Serializable

/**
 * リポジトリエントリ内のリリースチャンネル1件分の設定
 *
 * `repositories[i].latest`, `repositories[i].beta`, `repositories[i].alpha`
 * の各フィールドに指定され、そのチャンネルに属するバージョンを識別する
 * 正規表現パターンと、そのチャンネル固有のバージョン正規化パターンを提供する。
 *
 * `versionMatcher` が指定されている場合、mpmは `getAllVersions` の結果を
 * `versionMatcher` でフィルタし、最初にマッチしたバージョンをそのチャンネルの最新として扱う。
 * 指定がない場合は各プラットフォーム固有のチャンネル分類（Modrinthの `version_type`、
 * GitHubの `prerelease`）にフォールバックする。
 *
 * `versionModifier` が指定されている場合、そのチャンネルのバージョン文字列を
 * 正規化する際に、[RepositoryConfig.versionPattern] よりも優先して使用される。
 * チャンネルによってバージョン書式が異なる場合（例: stableは `X.Y.Z`、betaは
 * `X.Y.Z-beta.N`）に、チャンネルごとに別々のキャプチャパターンを定義できる。
 *
 * @property versionMatcher バージョン文字列に対する正規表現。`containsMatchIn` で評価される。
 *   例: `"-SNAPSHOT$"`, `"-beta\\."`, `"^\\d+\\.\\d+\\.\\d+$"`
 * @property versionModifier バージョン文字列からsemver相当部分を抽出する正規表現。
 *   未指定の場合は[RepositoryConfig.versionPattern]またはデフォルトのsemverパターンにフォールバック
 */
@Serializable
data class ChannelConfig(
    val versionMatcher: String? = null,
    val versionModifier: String? = null,
)
