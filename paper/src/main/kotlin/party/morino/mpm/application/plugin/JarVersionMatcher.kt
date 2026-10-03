/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

import party.morino.mpm.api.domain.downloader.model.VersionData
import party.morino.mpm.api.domain.plugin.model.VersionDetail

/**
 * JAR（plugin.yml）に書かれたバージョンを、リポジトリ上の実バージョンへ照合する純粋関数群
 *
 * plugin.yml のバージョンとリポジトリ上のバージョン名（GitHub のタグ名など）は表記が揃っているとは限らない。
 * `adopt --pin` と `init` 時の mpm 自身の登録の両方で同じ照合規則を使うため、ここにまとめている。
 */
internal object JarVersionMatcher {
    // "v" の除去は "v1.0.0" のように数字が続く場合のみ行う（"Version" 等の誤切断を防止）
    private val V_PREFIX_PATTERN = Regex("^[vV](?=\\d)")

    /**
     * JARのバージョンに一致するリポジトリ上のバージョンを探す
     *
     * 1. "v" prefixの有無を切り替えた候補で完全一致を探す（完全一致を優先）
     * 2. 見つからなければ versionPattern（またはデフォルトsemverパターン）で正規化して比較する
     *
     * @param jarVersion JARのplugin.ymlに書かれたバージョン
     * @param allVersions リポジトリ上の全バージョン
     * @param versionPattern リポジトリ設定のバージョン正規化パターン
     * @return 一致したバージョン（見つからない場合はnull）
     */
    fun findMatchingVersion(
        jarVersion: String,
        allVersions: List<VersionData>,
        versionPattern: String?
    ): VersionData? {
        // 表記揺れを考慮したバージョン候補で完全一致を探す
        val exactMatch =
            buildVersionCandidates(jarVersion).firstNotNullOfOrNull { candidate ->
                allVersions.firstOrNull { it.version == candidate }
            }
        if (exactMatch != null) return exactMatch

        // 完全一致が無い場合は正規化した値同士で比較する
        val jarNormalized = VersionDetail.normalizeWithPattern(jarVersion, versionPattern)
        return allVersions.firstOrNull { versionData ->
            VersionDetail.normalizeWithPattern(versionData.version, versionPattern) == jarNormalized
        }
    }

    /**
     * バージョン文字列から表記揺れ候補を生成する
     *
     * "v" prefixの有無を切り替えた候補を返す。
     */
    fun buildVersionCandidates(version: String): List<String> =
        buildList {
            add(version)
            if (V_PREFIX_PATTERN.containsMatchIn(version)) {
                // "v1.0.0" → "1.0.0" も候補に追加
                add(version.substring(1))
            } else if (version.firstOrNull()?.isDigit() == true) {
                // "1.0.0" → "v1.0.0" も候補に追加
                add("v$version")
            }
        }
}