/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mineauth.api.annotations.GetMapping
import party.morino.mineauth.api.annotations.PathParam
import party.morino.mineauth.api.annotations.Permission
import party.morino.mineauth.api.annotations.PostMapping
import party.morino.mineauth.api.annotations.QueryParams
import party.morino.mineauth.api.http.HttpError
import party.morino.mineauth.api.http.HttpStatus
import party.morino.mpm.api.application.model.PluginFilter
import party.morino.mpm.api.application.model.UpdateResult
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.application.plugin.PluginLifecycleService
import party.morino.mpm.api.application.plugin.PluginUpdateService
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.infrastructure.mineauth.model.InstallResultResponse
import party.morino.mpm.infrastructure.mineauth.model.OutdatedPluginResponse
import party.morino.mpm.infrastructure.mineauth.model.PluginSummaryResponse
import party.morino.mpm.infrastructure.mineauth.model.UninstallResponse

/**
 * HTTPクエリパラメータのbooleanを安全にパースする
 * "true", "1", "yes", "on" (大文字小文字問わず) を true として扱う
 */
private fun String.parseBooleanParam(): Boolean =
    when (this.lowercase()) {
        "true", "1", "yes", "on" -> true
        else -> false
    }

/**
 * mpm の MineAuth HTTP ハンドラー
 *
 * MineAuth の RegisterHandler API を通じて登録される。
 * エンドポイントは /api/v1/plugins/mpm/ 配下にマウントされる。
 * @Permission("mpm.api") によって保護され、service account は自動的にバイパスできる。
 */
@Permission("mpm.api")
class MpmPluginHandler : KoinComponent {
    // KoinによるDI
    private val pluginInfoService: PluginInfoService by inject()
    private val pluginUpdateService: PluginUpdateService by inject()
    private val pluginLifecycleService: PluginLifecycleService by inject()

    /**
     * 管理中のプラグイン一覧を取得する
     * GET /api/v1/plugins/mpm/plugins
     *
     * @return 管理中プラグインの一覧
     */
    @GetMapping("/plugins")
    suspend fun listPlugins(): List<PluginSummaryResponse> {
        val plugins = pluginInfoService.list(PluginFilter.ALL)
        return plugins.map { PluginSummaryResponse.from(it) }
    }

    /**
     * 更新可能なプラグインの一覧を取得する
     * GET /api/v1/plugins/mpm/plugins/outdated
     *
     * @return 更新可能なプラグインの一覧
     */
    @GetMapping("/plugins/outdated")
    suspend fun listOutdatedPlugins(): List<OutdatedPluginResponse> {
        val result =
            pluginInfoService.checkAllOutdated().fold(
                ifLeft = { error ->
                    throw HttpError(HttpStatus.INTERNAL_SERVER_ERROR, error.message)
                },
                ifRight = { it }
            )
        // 更新が必要なプラグインのみ返す
        return result.outdatedPlugins.map { OutdatedPluginResponse.from(it) }
    }

    /**
     * 全プラグインを一括更新する
     * POST /api/v1/plugins/mpm/plugins/update
     *
     * @param params クエリパラメータ（force=true で api-version 非互換でも強制更新）
     * @return 各プラグインの更新結果一覧
     */
    @PostMapping("/plugins/update")
    suspend fun updateAllPlugins(
        @QueryParams params: Map<String, String>
    ): List<UpdateResult> {
        val force = params["force"]?.parseBooleanParam() ?: false
        return pluginUpdateService.update(force = force).fold(
            ifLeft = { error ->
                throw HttpError(HttpStatus.INTERNAL_SERVER_ERROR, error.message)
            },
            ifRight = { it }
        )
    }

    /**
     * 指定したプラグインを更新する
     * POST /api/v1/plugins/mpm/plugins/{name}/update
     *
     * @param name 更新対象のプラグイン名
     * @param params クエリパラメータ（force=true で強制更新）
     * @return 更新結果
     */
    @PostMapping("/plugins/{name}/update")
    suspend fun updatePlugin(
        @PathParam("name") name: String,
        @QueryParams params: Map<String, String>
    ): UpdateResult {
        val force = params["force"]?.parseBooleanParam() ?: false
        return pluginUpdateService.update(PluginName(name), force = force).fold(
            ifLeft = { error ->
                // プラグインが見つからない場合は 404 を返す
                val status =
                    if (error.message.contains("not found", ignoreCase = true)) {
                        HttpStatus.NOT_FOUND
                    } else {
                        HttpStatus.INTERNAL_SERVER_ERROR
                    }
                throw HttpError(status, error.message)
            },
            ifRight = { it }
        )
    }

    /**
     * 指定したプラグインをインストール（ダウンロード配置）する
     * POST /api/v1/plugins/mpm/plugins/{name}/install
     *
     * @param name インストール対象のプラグイン名
     * @param params クエリパラメータ（force=true で api-version 非互換でも強制インストール）
     * @return インストール結果
     */
    @PostMapping("/plugins/{name}/install")
    suspend fun installPlugin(
        @PathParam("name") name: String,
        @QueryParams params: Map<String, String>
    ): InstallResultResponse {
        val force = params["force"]?.parseBooleanParam() ?: false
        val result =
            pluginLifecycleService.install(PluginName(name), force = force).fold(
                ifLeft = { error ->
                    val status =
                        if (error.message.contains("not found", ignoreCase = true)) {
                            HttpStatus.NOT_FOUND
                        } else {
                            HttpStatus.INTERNAL_SERVER_ERROR
                        }
                    throw HttpError(status, error.message)
                },
                ifRight = { it }
            )
        return InstallResultResponse.from(result)
    }

    /**
     * 指定したプラグインをアンインストール（ファイル削除）する
     * POST /api/v1/plugins/mpm/plugins/{name}/uninstall
     *
     * @param name アンインストール対象のプラグイン名
     * @return アンインストール結果
     */
    @PostMapping("/plugins/{name}/uninstall")
    suspend fun uninstallPlugin(
        @PathParam("name") name: String
    ): UninstallResponse {
        pluginLifecycleService.uninstall(PluginName(name)).fold(
            ifLeft = { error ->
                val status =
                    if (error.message.contains("not found", ignoreCase = true)) {
                        HttpStatus.NOT_FOUND
                    } else {
                        HttpStatus.INTERNAL_SERVER_ERROR
                    }
                throw HttpError(status, error.message)
            },
            ifRight = { }
        )
        return UninstallResponse(
            name = name,
            message = "Plugin '$name' uninstalled successfully. Restart the server to apply changes."
        )
    }
}