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
import party.morino.mineauth.api.CallerType
import party.morino.mineauth.api.annotations.Authenticated
import party.morino.mineauth.api.annotations.Body
import party.morino.mineauth.api.annotations.Get
import party.morino.mineauth.api.annotations.Path
import party.morino.mineauth.api.annotations.Post
import party.morino.mineauth.api.annotations.Query
import party.morino.mineauth.api.annotations.QueryMap
import party.morino.mineauth.api.http.HttpError
import party.morino.mineauth.api.http.HttpStatus
import party.morino.mpm.api.application.dependency.DependencyService
import party.morino.mpm.api.application.health.DoctorService
import party.morino.mpm.api.application.model.PluginFilter
import party.morino.mpm.api.application.model.UpdateResult
import party.morino.mpm.api.application.plugin.PluginInfoService
import party.morino.mpm.api.application.plugin.PluginLifecycleService
import party.morino.mpm.api.application.plugin.PluginUpdateService
import party.morino.mpm.api.application.search.PluginSearchService
import party.morino.mpm.api.domain.plugin.dto.version.HistoryEntryDto
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.domain.plugin.model.VersionDetail
import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import party.morino.mpm.api.domain.repository.RepositoryManager
import party.morino.mpm.infrastructure.mineauth.model.dependency.DependencyResponse
import party.morino.mpm.infrastructure.mineauth.model.health.DoctorReportResponse
import party.morino.mpm.infrastructure.mineauth.model.health.VerifyEntryResponse
import party.morino.mpm.infrastructure.mineauth.model.lifecycle.InstallResultResponse
import party.morino.mpm.infrastructure.mineauth.model.lifecycle.LockStateResponse
import party.morino.mpm.infrastructure.mineauth.model.lifecycle.UninstallResponse
import party.morino.mpm.infrastructure.mineauth.model.lifecycle.VersionSwitchRequest
import party.morino.mpm.infrastructure.mineauth.model.outdated.OutdatedCheckResponse
import party.morino.mpm.infrastructure.mineauth.model.plugin.PluginDetailResponse
import party.morino.mpm.infrastructure.mineauth.model.plugin.PluginMetadataResponse
import party.morino.mpm.infrastructure.mineauth.model.plugin.PluginSummaryResponse
import party.morino.mpm.infrastructure.mineauth.model.repository.RepositorySourceResponse
import party.morino.mpm.infrastructure.mineauth.model.search.PluginSearchResultResponse

// 検索エンドポイントの既定取得件数
private const val DEFAULT_SEARCH_LIMIT = 10

// 検索エンドポイントの最大取得件数（過大なリクエストで上流APIを叩きすぎないための上限）
private const val MAX_SEARCH_LIMIT = 50

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
 * `filter` クエリパラメータを [PluginFilter] に変換する
 *
 * 大文字小文字は区別しない。未指定（null）の場合は従来どおり [PluginFilter.ALL] を用いる。
 *
 * @param filter クエリパラメータの値
 * @return 対応する [PluginFilter]
 * @throws HttpError 未知の値が指定された場合（400 Bad Request）
 */
private fun parsePluginFilter(filter: String?): PluginFilter {
    if (filter == null) return PluginFilter.ALL
    return PluginFilter.entries.firstOrNull { it.name.equals(filter, ignoreCase = true) }
        ?: throw HttpError(
            HttpStatus.BAD_REQUEST,
            "Unknown filter '$filter'. Supported values: " +
                PluginFilter.entries.joinToString(", ") { it.name.lowercase() }
        )
}

/**
 * mpm の MineAuth HTTP ハンドラー
 *
 * MineAuth v2 API（[party.morino.mineauth.api.MineAuthApi.register]）を通じて登録される。
 * エンドポイントは /api/v1/plugins/mpm/ 配下にマウントされる。
 *
 * MineAuth v2 では全エンドポイントがアクセス宣言を必須とするため、各メソッドに
 * `@Authenticated(permission = ..., callers = [USER, SERVICE])` を付与する。
 * パーミッションは読み取り専用の [MpmApiPermission.READ] と、サーバー状態を変更する
 * [MpmApiPermission.WRITE] に分割している（従来の `mpm.api` は両方を含む親として維持）。
 *
 * 重要な制約（mineauth-api 0.3.6 の `AuthenticationHandler` 仕様）:
 * `permission` はユーザープリンシパルにしか評価されない。`callers` に含まれる
 * サービストークンはパーミッションチェックを完全にバイパスするため、
 * サービストークンにとっては `callers` リストが唯一のアクセス制御である。
 * 読み書きの分割はユーザートークンに対してのみ有効であり、これは mineauth-api 側の
 * 制約のため mpm 側では解消できない。
 */
class MpmPluginHandler : KoinComponent {
    // KoinによるDI
    private val pluginInfoService: PluginInfoService by inject()
    private val pluginUpdateService: PluginUpdateService by inject()
    private val pluginLifecycleService: PluginLifecycleService by inject()
    private val doctorService: DoctorService by inject()
    private val pluginSearchService: PluginSearchService by inject()
    private val dependencyService: DependencyService by inject()
    private val repositoryManager: RepositoryManager by inject()
    private val pluginMetadataManager: PluginMetadataManager by inject()

    // ===== 読み取り系エンドポイント（mpm.api.read） =====

    /**
     * プラグイン一覧を取得する
     * GET /api/v1/plugins/mpm/plugins
     *
     * `filter` を省略した場合は従来どおり [PluginFilter.ALL]（管理下のプラグイン）を返す。
     * 管理外プラグインを取得したい場合は `filter=unmanaged` を明示的に指定する。
     *
     * @param filter 絞り込み条件（all / managed / unmanaged / outdated / locked、大文字小文字非依存）
     * @return プラグインの一覧
     */
    @Get("/plugins")
    @Authenticated(permission = MpmApiPermission.READ, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun listPlugins(
        @Query("filter") filter: String?
    ): List<PluginSummaryResponse> {
        val plugins = pluginInfoService.list(parsePluginFilter(filter))
        return plugins.map { PluginSummaryResponse.from(it) }
    }

    /**
     * 更新チェックの結果を取得する
     * GET /api/v1/plugins/mpm/plugins/outdated
     *
     * チェックに成功したプラグイン（`outdated`）と失敗したプラグイン（`errors`）を返す。
     * 各エントリの `needsUpdate` が実際に更新が必要かどうかを表す。
     *
     * @return 更新チェック結果
     */
    @Get("/plugins/outdated")
    @Authenticated(permission = MpmApiPermission.READ, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun listOutdatedPlugins(): OutdatedCheckResponse {
        val result = pluginInfoService.checkAllOutdated().orThrowHttpError()
        return OutdatedCheckResponse.from(result)
    }

    /**
     * インストール済みプラグインの整合性を再検証する
     * GET /api/v1/plugins/mpm/plugins/verify
     *
     * ネットワークアクセスは行わず、ローカルのJARのsha256とメタデータを照合する。
     *
     * @return プラグインごとの検証結果一覧
     */
    @Get("/plugins/verify")
    @Authenticated(permission = MpmApiPermission.READ, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun verifyPlugins(): List<VerifyEntryResponse> =
        pluginInfoService.verifyInstalled().orThrowHttpError().map { VerifyEntryResponse.from(it) }

    /**
     * サーバーのプラグイン管理状態を一括診断する
     * GET /api/v1/plugins/mpm/doctor
     *
     * @return 診断結果
     */
    @Get("/doctor")
    @Authenticated(permission = MpmApiPermission.READ, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun diagnose(): DoctorReportResponse =
        DoctorReportResponse.from(doctorService.diagnose().orThrowHttpError())

    /**
     * リポジトリを横断してプラグインを検索する
     * GET /api/v1/plugins/mpm/search?q=...&limit=...
     *
     * @param query 検索キーワード（必須。省略時は 400 Bad Request）
     * @param limit 取得件数（省略時は [DEFAULT_SEARCH_LIMIT]、上限は [MAX_SEARCH_LIMIT]）
     * @return 検索結果（ダウンロード数の多い順）
     */
    @Get("/search")
    @Authenticated(permission = MpmApiPermission.READ, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun searchPlugins(
        @Query("q") query: String,
        @Query("limit") limit: Int?
    ): List<PluginSearchResultResponse> {
        // 空文字での検索は上流APIに無意味な負荷をかけるため弾く
        if (query.isBlank()) {
            throw HttpError(HttpStatus.BAD_REQUEST, "Query parameter 'q' must not be blank")
        }
        // 上限・下限でクランプして上流APIへの過大なリクエストを防ぐ
        val effectiveLimit = (limit ?: DEFAULT_SEARCH_LIMIT).coerceIn(1, MAX_SEARCH_LIMIT)
        return pluginSearchService
            .search(query, effectiveLimit)
            .orThrowHttpError()
            .map { PluginSearchResultResponse.from(it) }
    }

    /**
     * 設定されているリポジトリソースの一覧を取得する
     * GET /api/v1/plugins/mpm/repositories
     *
     * @return リポジトリソースの一覧（優先順位順）
     */
    @Get("/repositories")
    @Authenticated(permission = MpmApiPermission.READ, callers = [CallerType.USER, CallerType.SERVICE])
    fun listRepositories(): List<RepositorySourceResponse> =
        repositoryManager.getRepositorySources().map { RepositorySourceResponse.from(it) }

    /**
     * 指定したプラグインの詳細情報を取得する
     * GET /api/v1/plugins/mpm/plugins/{name}
     *
     * リポジトリからプロジェクト情報を取得するため、ネットワークアクセスが発生する。
     *
     * @param name プラグイン名
     * @return プラグイン詳細
     */
    @Get("/plugins/{name}")
    @Authenticated(permission = MpmApiPermission.READ, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun getPluginDetail(
        @Path("name") name: String
    ): PluginDetailResponse =
        PluginDetailResponse.from(pluginInfoService.getPluginDetail(PluginName(name)).orThrowHttpError())

    /**
     * 指定したプラグインの利用可能なバージョン一覧を取得する
     * GET /api/v1/plugins/mpm/plugins/{name}/versions
     *
     * 注意: バージョンはリポジトリ定義の最初のソースからのみ解決される。
     * 複数のダウンロード元が設定されたプラグインでは、2番目以降のソースのバージョンは返らない。
     *
     * @param name プラグイン名
     * @return バージョン一覧（新しい順）
     */
    @Get("/plugins/{name}/versions")
    @Authenticated(permission = MpmApiPermission.READ, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun getPluginVersions(
        @Path("name") name: String
    ): List<VersionDetail> = pluginInfoService.getVersions(PluginName(name)).orThrowHttpError()

    /**
     * 指定したプラグインのメタデータを取得する
     * GET /api/v1/plugins/mpm/plugins/{name}/metadata
     *
     * `metadata/<plugin>.yaml` に記録されたダウンロード元・sha256・履歴を返す。
     *
     * @param name プラグイン名
     * @return メタデータ
     */
    @Get("/plugins/{name}/metadata")
    @Authenticated(permission = MpmApiPermission.READ, callers = [CallerType.USER, CallerType.SERVICE])
    fun getPluginMetadata(
        @Path("name") name: String
    ): PluginMetadataResponse {
        // PluginMetadataManagerはエラーを文字列で返すため、ここで404に変換する
        // （読み込み失敗の大半はメタデータファイルの不在によるもの）
        val metadata =
            pluginMetadataManager.loadMetadata(name).fold(
                ifLeft = { reason ->
                    throw HttpError(HttpStatus.NOT_FOUND, "Metadata not found for '$name': $reason")
                },
                ifRight = { it }
            )
        return PluginMetadataResponse.from(metadata)
    }

    /**
     * 指定したプラグインのインストール履歴を取得する
     * GET /api/v1/plugins/mpm/plugins/{name}/history
     *
     * @param name プラグイン名
     * @return 履歴エントリ一覧（古い順）
     */
    @Get("/plugins/{name}/history")
    @Authenticated(permission = MpmApiPermission.READ, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun getPluginHistory(
        @Path("name") name: String
    ): List<HistoryEntryDto> = pluginInfoService.getHistory(PluginName(name)).orThrowHttpError()

    /**
     * 指定したプラグインの依存関係を取得する
     * GET /api/v1/plugins/mpm/plugins/{name}/deps
     *
     * 依存関係タブが1リクエストで描画できるよう、依存情報・ツリー・逆依存・依存経路をまとめて返す。
     *
     * @param name プラグイン名
     * @param soft true の場合、softDepend も依存ツリーに含める
     * @return 依存関係情報
     */
    @Get("/plugins/{name}/deps")
    @Authenticated(permission = MpmApiPermission.READ, callers = [CallerType.USER, CallerType.SERVICE])
    fun getPluginDependencies(
        @Path("name") name: String,
        @Query("soft") soft: Boolean?
    ): DependencyResponse {
        val includeSoft = soft ?: false
        // 依存情報が取れない場合は対象プラグインが存在しないため、ここでエラーを返す
        val info =
            dependencyService.getDependencyInfo(name).fold(
                ifLeft = { error -> throw HttpError(error.toHttpStatus(), error.toString()) },
                ifRight = { it }
            )
        // ツリーと依存経路は失敗しても致命的ではないため、取得できなければ空扱いにする
        val tree = dependencyService.buildDependencyTree(name, includeSoft).getOrNull()
        val chains = dependencyService.getDependencyChains(name).getOrNull().orEmpty()
        return DependencyResponse(
            name = name,
            info = info,
            tree = tree,
            reverseDependencies = dependencyService.getReverseDependencies(name),
            dependencyChains = chains
        )
    }

    // ===== 書き込み系エンドポイント（mpm.api.write） =====

    /**
     * 全プラグインを一括更新する
     * POST /api/v1/plugins/mpm/plugins/update
     *
     * 注意: この処理は管理下の全プラグインをダウンロード・検証・配置するため、
     * プラグイン数によっては数分単位の時間がかかる。処理が完了するまでレスポンスは返らず、
     * 進捗通知やジョブIDの仕組みも提供していない。リバースプロキシやブラウザのタイムアウトに
     * 到達すると、クライアントは更新の成否を知る手段がなくなる。
     * そのため、ブラウザからこのエンドポイントを直接呼び出すことは推奨しない。
     * web console からはプラグインごとの `POST /plugins/{name}/update` を利用すること。
     *
     * @param params クエリパラメータ（force=true で api-version 非互換でも強制更新）
     * @return 各プラグインの更新結果一覧
     */
    @Post("/plugins/update")
    @Authenticated(permission = MpmApiPermission.WRITE, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun updateAllPlugins(
        @QueryMap params: Map<String, String>
    ): List<UpdateResult> {
        val force = params["force"]?.parseBooleanParam() ?: false
        return pluginUpdateService.update(force = force).orThrowHttpError()
    }

    /**
     * 指定したプラグインを更新する
     * POST /api/v1/plugins/mpm/plugins/{name}/update
     *
     * 対象プラグインに同期している sync: プラグイン（子）があれば、親の更新後バージョンに
     * 追従して連動更新する。レスポンスは先頭が親、以降が連動更新した子の結果となる。
     *
     * @param name 更新対象のプラグイン名
     * @param params クエリパラメータ（force=true で強制更新）
     * @return 更新結果一覧（親＋連動更新した子）
     */
    @Post("/plugins/{name}/update")
    @Authenticated(permission = MpmApiPermission.WRITE, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun updatePlugin(
        @Path("name") name: String,
        @QueryMap params: Map<String, String>
    ): List<UpdateResult> {
        val force = params["force"]?.parseBooleanParam() ?: false
        return pluginUpdateService.update(PluginName(name), force = force).orThrowHttpError()
    }

    /**
     * 管理下プラグインを指定したバージョンに切り替える
     * POST /api/v1/plugins/mpm/plugins/{name}/version
     *
     * アップグレードとダウングレードを区別せず、「このプラグインをバージョン X にする」という
     * 1つの操作として扱う。切り替え後は mpm.json のバージョン指定が固定（Fixed）になる。
     *
     * @param name 対象のプラグイン名
     * @param request 切り替え先バージョンと各種オプション
     * @return 切り替え結果
     */
    @Post("/plugins/{name}/version")
    @Authenticated(permission = MpmApiPermission.WRITE, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun switchPluginVersion(
        @Path("name") name: String,
        @Body request: VersionSwitchRequest
    ): UpdateResult {
        // 空のバージョン指定は解決不能なので、サービスを呼ぶ前に弾く
        if (request.version.isBlank()) {
            throw HttpError(HttpStatus.BAD_REQUEST, "Field 'version' must not be blank")
        }
        return pluginUpdateService
            .switchVersion(
                name = PluginName(name),
                version = request.version,
                force = request.force,
                skipIntegrity = request.skipIntegrity
            ).orThrowHttpError()
    }

    /**
     * 指定したプラグインをインストール（ダウンロード配置）する
     * POST /api/v1/plugins/mpm/plugins/{name}/install
     *
     * @param name インストール対象のプラグイン名
     * @param params クエリパラメータ（force=true で api-version 非互換でも強制インストール）
     * @return インストール結果
     */
    @Post("/plugins/{name}/install")
    @Authenticated(permission = MpmApiPermission.WRITE, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun installPlugin(
        @Path("name") name: String,
        @QueryMap params: Map<String, String>
    ): InstallResultResponse {
        val force = params["force"]?.parseBooleanParam() ?: false
        val result = pluginLifecycleService.install(PluginName(name), force = force).orThrowHttpError()
        return InstallResultResponse.from(result)
    }

    /**
     * 指定したプラグインをアンインストール（ファイル削除）する
     * POST /api/v1/plugins/mpm/plugins/{name}/uninstall
     *
     * @param name アンインストール対象のプラグイン名
     * @return アンインストール結果
     */
    @Post("/plugins/{name}/uninstall")
    @Authenticated(permission = MpmApiPermission.WRITE, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun uninstallPlugin(
        @Path("name") name: String
    ): UninstallResponse {
        pluginLifecycleService.uninstall(PluginName(name)).orThrowHttpError()
        return UninstallResponse(
            name = name,
            message = "Plugin '$name' uninstalled successfully. Restart the server to apply changes."
        )
    }

    /**
     * 指定したプラグインをロックする（自動更新の対象外にする）
     * POST /api/v1/plugins/mpm/plugins/{name}/lock
     *
     * すでにロック済みの場合は 409 Conflict を返す。
     *
     * @param name 対象のプラグイン名
     * @return ロック後の状態
     */
    @Post("/plugins/{name}/lock")
    @Authenticated(permission = MpmApiPermission.WRITE, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun lockPlugin(
        @Path("name") name: String
    ): LockStateResponse {
        pluginUpdateService.lock(PluginName(name)).orThrowHttpError()
        return LockStateResponse(
            name = name,
            isLocked = true,
            message = "Plugin '$name' has been locked."
        )
    }

    /**
     * 指定したプラグインのロックを解除する
     * POST /api/v1/plugins/mpm/plugins/{name}/unlock
     *
     * ロックされていない場合は 409 Conflict を返す。
     *
     * @param name 対象のプラグイン名
     * @return ロック解除後の状態
     */
    @Post("/plugins/{name}/unlock")
    @Authenticated(permission = MpmApiPermission.WRITE, callers = [CallerType.USER, CallerType.SERVICE])
    suspend fun unlockPlugin(
        @Path("name") name: String
    ): LockStateResponse {
        pluginUpdateService.unlock(PluginName(name)).orThrowHttpError()
        return LockStateResponse(
            name = name,
            isLocked = false,
            message = "Plugin '$name' has been unlocked."
        )
    }
}