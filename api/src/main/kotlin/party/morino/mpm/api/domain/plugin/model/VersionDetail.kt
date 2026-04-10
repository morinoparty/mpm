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

package party.morino.mpm.api.domain.plugin.model

import kotlinx.serialization.Serializable

/**
 * プラグインバージョンの詳細情報を表すValue Object
 *
 * @property raw 元のバージョン文字列（リポジトリから取得したまま）
 * @property normalized 正規化されたバージョン文字列（比較用）
 */
@Serializable
data class VersionDetail(
    val raw: String,
    val normalized: String
) {
    companion object {
        // デフォルトのバージョン抽出パターン（セマンティックバージョニング）
        // Repository File Generatorのデフォルト値と同一
        const val DEFAULT_VERSION_PATTERN = "(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"

        /**
         * versionPatternを使ってバージョン文字列を正規化する
         * パターンが未指定の場合はデフォルトのsemverパターンで抽出する
         * PluginMetadataManagerImplと同じロジック: Regex.find()?.value
         *
         * @param raw 元のバージョン文字列
         * @param versionPattern バージョン番号の抽出用正規表現（nullの場合はデフォルトsemverパターン）
         */
        fun normalizeWithPattern(raw: String, versionPattern: String? = null): String {
            val pattern = if (versionPattern.isNullOrBlank()) DEFAULT_VERSION_PATTERN else versionPattern
            return runCatching {
                Regex(pattern).find(raw)?.value
            }.getOrNull() ?: raw
        }

        /**
         * rawバージョン文字列からVersionDetailを作成
         * 正規化処理を自動的に行う
         *
         * @param raw 元のバージョン文字列
         * @param versionPattern バージョン番号の抽出用正規表現（省略時はデフォルトsemverパターン）
         */
        fun fromRaw(raw: String, versionPattern: String? = null): VersionDetail {
            val normalized = normalizeWithPattern(raw, versionPattern)
            return VersionDetail(raw = raw, normalized = normalized)
        }
    }

    /**
     * バージョンが等しいかどうかを正規化された値で比較
     */
    fun equalsNormalized(other: VersionDetail): Boolean = this.normalized == other.normalized

    override fun toString(): String = raw
}