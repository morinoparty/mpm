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

package party.morino.mpm.application.plugin

/**
 * [PluginInstallValidator] によるダウンロード済みプラグインJARの事前検証結果
 *
 * PluginLifecycleServiceImpl.install() と PluginUpdateServiceImpl の更新処理の両方から
 * 共通のロジックとして利用され、それぞれの呼び出し元で自身のエラー表現（MpmError / String）に変換される
 */
sealed class PluginInstallValidationResult {
    /**
     * 検証に成功した場合（互換性・依存関係ともに問題ない、またはforce指定により許容された場合）
     */
    data object Valid : PluginInstallValidationResult()

    /**
     * APIバージョンが非互換であり、forceが指定されていないため失敗した場合
     *
     * @param pluginApiVersion プラグインが要求するAPIバージョン
     * @param serverApiVersion サーバーが提供するAPIバージョン
     */
    data class ApiVersionIncompatible(
        val pluginApiVersion: String,
        val serverApiVersion: String
    ) : PluginInstallValidationResult()

    /**
     * 必須依存プラグインが不足しており、forceが指定されていないため失敗した場合
     *
     * @param missingDependencies 管理対象にもインストール済みにも見つからなかった依存プラグイン名
     */
    data class MissingDependencies(
        val missingDependencies: List<String>
    ) : PluginInstallValidationResult()
}