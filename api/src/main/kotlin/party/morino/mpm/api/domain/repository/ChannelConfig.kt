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
 * 正規表現パターン（および/またはプラットフォーム固有のラベル参照）と、
 * そのチャンネル固有のバージョン正規化パターンを提供する。
 *
 * 解決の優先順位:
 * 1. `versionMatcher` が指定されている場合、`getAllVersions` の結果をregex
 *    (`containsMatchIn`) でフィルタし、最初にマッチしたバージョンを返す。
 * 2. `useUpstreamLabel` が `true` の場合、プラットフォーム固有のチャンネルラベルを
 *    参照する（Modrinthの `version_type`、GitHubの `prerelease` フラグ）。
 *    `latest` チャンネルは `getLatestVersion` に、`beta`/`alpha` は
 *    `getLatestVersionByTag` に委譲される。
 * 3. どちらも指定されていない場合、このチャンネル定義はスキップされ、呼び出し側は
 *    既存のデフォルト挙動にフォールバックする。
 *
 * `versionModifier` が指定されている場合、そのチャンネルのバージョン文字列を
 * 正規化する際に、[RepositoryConfig.versionPattern] よりも優先して使用される。
 *
 * @property versionMatcher バージョン文字列に対する正規表現。`containsMatchIn` で評価される。
 *   例: `"-SNAPSHOT$"`, `"-beta\\."`, `"^\\d+\\.\\d+\\.\\d+$"`
 * @property versionModifier バージョン文字列からsemver相当部分を抽出する正規表現。
 *   未指定の場合は[RepositoryConfig.versionPattern]またはデフォルトのsemverパターンにフォールバック
 * @property useUpstreamLabel `true` の場合、プラットフォーム固有のチャンネルラベルを
 *   使用してバージョンを解決する（必要な場合のみopt-inで指定する）。
 *   GitHubでは `prerelease` フラグ、Modrinthでは `version_type` フィールドが使われる。
 *   `versionMatcher` が指定されていればそちらが優先される。
 */
@Serializable
data class ChannelConfig(
    val versionMatcher: String? = null,
    val versionModifier: String? = null,
    val useUpstreamLabel: Boolean = false
)