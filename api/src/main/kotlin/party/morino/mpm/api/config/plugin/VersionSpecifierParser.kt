/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related
 * and neighboring rights to this software to the public domain worldwide.
 * This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.config.plugin

/**
 * バージョン文字列をVersionSpecifierにパースするためのユーティリティオブジェクト
 *
 * mpm.jsonやコマンドライン引数で指定されたバージョン文字列を
 * 適切なVersionSpecifierインスタンスに変換する
 */
object VersionSpecifierParser {
    // プレフィックス定数（大文字小文字を区別しない比較用）
    private const val SYNC_PREFIX = "sync:"
    private const val TAG_PREFIX = "tag:"
    private const val PATTERN_PREFIX = "pattern:"

    /**
     * バージョン文字列をVersionSpecifierにパースする
     *
     * @param versionString パース対象のバージョン文字列
     * @return パースされたVersionSpecifier
     *
     * サポートされる形式:
     * - "latest" → VersionSpecifier.Latest
     * - "sync:PluginName" → VersionSpecifier.Sync
     * - "tag:stable" → VersionSpecifier.Tag
     * - "pattern:^5\\.4\\..*" → VersionSpecifier.Pattern
     * - その他 → VersionSpecifier.Fixed
     */
    fun parse(versionString: String): VersionSpecifier = when {
        versionString.equals("latest", ignoreCase = true) -> VersionSpecifier.Latest
        versionString.startsWith(SYNC_PREFIX, ignoreCase = true) ->
            versionString.drop(SYNC_PREFIX.length)
                .takeIf { it.isNotBlank() }
                ?.let { VersionSpecifier.Sync(it) }
                ?: VersionSpecifier.Fixed(versionString)
        versionString.startsWith(TAG_PREFIX, ignoreCase = true) ->
            VersionSpecifier.Tag(versionString.drop(TAG_PREFIX.length))
        versionString.startsWith(PATTERN_PREFIX, ignoreCase = true) ->
            VersionSpecifier.Pattern(versionString.drop(PATTERN_PREFIX.length))
        else -> VersionSpecifier.Fixed(versionString)
    }

    /**
     * VersionSpecifierをバージョン文字列に変換する
     *
     * @param specifier 変換対象のVersionSpecifier
     * @return バージョン文字列
     */
    fun toVersionString(specifier: VersionSpecifier): String = when (specifier) {
        is VersionSpecifier.Latest -> "latest"
        is VersionSpecifier.Sync -> "sync:${specifier.targetPlugin}"
        is VersionSpecifier.Tag -> "tag:${specifier.tag}"
        is VersionSpecifier.Pattern -> "pattern:${specifier.pattern}"
        is VersionSpecifier.Fixed -> specifier.version
    }

    /**
     * バージョン文字列がSync形式かどうかを判定する
     *
     * @param versionString 判定対象のバージョン文字列
     * @return Sync形式の場合はtrue
     */
    fun isSyncFormat(versionString: String): Boolean =
        versionString.startsWith(SYNC_PREFIX, ignoreCase = true) &&
            versionString.length > SYNC_PREFIX.length

    /**
     * Sync形式のバージョン文字列からターゲットプラグイン名を抽出する
     *
     * @param versionString Sync形式のバージョン文字列
     * @return ターゲットプラグイン名、Sync形式でない場合はnull
     */
    fun extractSyncTarget(versionString: String): String? =
        if (isSyncFormat(versionString)) versionString.drop(SYNC_PREFIX.length) else null
}
