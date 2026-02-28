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

package party.morino.mpm.infrastructure.compatibility

import org.bukkit.Bukkit
import org.koin.core.component.KoinComponent
import party.morino.mpm.api.domain.compatibility.ApiVersionChecker
import party.morino.mpm.api.domain.compatibility.CompatibilityResult
import party.morino.mpm.api.model.plugin.PluginData
import party.morino.mpm.utils.PluginDataUtils
import java.io.File

/**
 * Bukkit/Paper環境でのAPIバージョン互換性チェッカー実装
 *
 * JARファイルからapi-versionを抽出し、サーバーのMinecraftバージョンと比較する
 */
class ApiVersionCheckerImpl : ApiVersionChecker, KoinComponent {
    // サーバーバージョンは起動後不変のため、初回取得時にキャッシュ（スレッドセーフティ確保）
    private val cachedServerApiVersion: String by lazy { resolveServerApiVersion() }

    /**
     * JARファイルからapi-versionを抽出し、サーバーのバージョンと比較する
     */
    override fun checkCompatibility(jarFile: File): CompatibilityResult {
        // 壊れたJARや不正なYAMLでも例外を拾いUnknownに落とす
        return try {
            // JARからプラグインデータを抽出
            val pluginData = PluginDataUtils.getPluginData(jarFile)
                ?: return CompatibilityResult.Unknown("plugin.yml or paper-plugin.yml not found")

            // api-versionを取得
            val pluginApiVersion = when (pluginData) {
                is PluginData.BukkitPluginData -> pluginData.apiVersion
                is PluginData.PaperPluginData -> pluginData.apiVersion
            }

            // api-versionが未指定の場合は判定不能
            if (pluginApiVersion.isBlank()) {
                return CompatibilityResult.Unknown("api-version is not specified")
            }

            val serverApiVersion = getServerApiVersion()

            // バージョンを比較
            compareVersions(pluginApiVersion, serverApiVersion)
        } catch (e: Exception) {
            // JARの読み込みやYAMLパースに失敗した場合は判定不能として扱う
            CompatibilityResult.Unknown("Failed to read plugin data: ${e.message}")
        }
    }

    /**
     * キャッシュ済みのサーバーAPIバージョンを返す
     */
    override fun getServerApiVersion(): String = cachedServerApiVersion

    /**
     * サーバーのMinecraftバージョンからAPIバージョンを解決する
     *
     * Bukkit.getMinecraftVersion()は"1.21.4"のような完全バージョンを返すため、
     * "1.21"のようなmajor.minor形式に変換する
     */
    private fun resolveServerApiVersion(): String {
        // "1.21.4" -> "1.21"
        val fullVersion = Bukkit.getMinecraftVersion()
        val parts = fullVersion.split(".")
        return if (parts.size >= 2) "${parts[0]}.${parts[1]}" else fullVersion
    }

    companion object {
        /**
         * api-versionを比較する純粋関数
         *
         * プラグインのapi-versionがサーバーのapi-version以下であれば互換と判定する
         * Minecraftではapi-versionは"major.minor"形式（例: "1.20", "1.21"）
         */
        internal fun compareVersions(
            pluginVersion: String,
            serverVersion: String
        ): CompatibilityResult {
            // バージョン文字列を数値リストに変換
            val pluginParts = pluginVersion.split(".").mapNotNull { it.toIntOrNull() }
            val serverParts = serverVersion.split(".").mapNotNull { it.toIntOrNull() }

            // パースに失敗した場合は判定不能
            if (pluginParts.size < 2 || serverParts.size < 2) {
                return CompatibilityResult.Unknown(
                    "Cannot parse version format (plugin=$pluginVersion, server=$serverVersion)"
                )
            }

            val (pluginMajor, pluginMinor) = pluginParts
            val (serverMajor, serverMinor) = serverParts

            // メジャーバージョンが一致し、プラグインのマイナーバージョンがサーバー以下なら互換
            return if (pluginMajor == serverMajor && pluginMinor <= serverMinor) {
                CompatibilityResult.Compatible
            } else {
                CompatibilityResult.Incompatible(
                    pluginApiVersion = pluginVersion,
                    serverApiVersion = serverVersion
                )
            }
        }
    }
}
