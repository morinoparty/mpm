/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
and related and neighboring rights to this software to the public domain worldwide.
This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.core.plugin

import arrow.core.Either
import party.morino.mpm.api.core.plugin.PluginInfoManager
import party.morino.mpm.api.model.plugin.InstalledPlugin
import party.morino.mpm.api.model.plugin.OutdatedInfo
import party.morino.mpm.api.model.plugin.PluginData
import party.morino.mpm.api.model.plugin.RepositoryPlugin
import party.morino.mpm.core.plugin.usecase.CheckOutdatedUseCaseImpl
import party.morino.mpm.core.plugin.usecase.PluginListUseCaseImpl
import party.morino.mpm.core.plugin.usecase.PluginVersionsUseCaseImpl

/**
 * PluginInfoManagerの実装クラス
 * プラグイン情報の取得、リスト表示、バージョン確認を担当
 */
class PluginInfoManagerImpl : PluginInfoManager {
    // UseCaseImplを直接インスタンス化（KoinComponentなので、内部でRepository等をinject()できる）
    private val pluginListUseCase = PluginListUseCaseImpl()
    private val pluginVersionsUseCase = PluginVersionsUseCaseImpl()
    private val checkOutdatedUseCase = CheckOutdatedUseCaseImpl()

    // キャッシュ用のプロパティ
    private var cachedManagedPlugins: List<PluginData>? = null
    private var cacheExpirationTime: Long = 0
    private val cacheTtlMillis = 180_000L // 3分 = 180秒 = 180,000ミリ秒

    override suspend fun getAllManagedPlugins(): List<PluginData> {
        // 現在時刻を取得
        val currentTime = System.currentTimeMillis()

        // キャッシュが有効かチェック
        if (cachedManagedPlugins != null && currentTime < cacheExpirationTime) {
            return cachedManagedPlugins!!
        }

        // PluginListUseCaseに処理を委譲
        val result = pluginListUseCase.getAllManagedPlugins()

        // 結果をキャッシュに保存
        cachedManagedPlugins = result
        cacheExpirationTime = currentTime + cacheTtlMillis

        return result
    }

    override fun getAllServerPlugins(): Map<String, Boolean> =
        // PluginListUseCaseに処理を委譲
        pluginListUseCase.getAllServerPlugins()

    override suspend fun getUnmanagedPlugins(): List<String> =
        // PluginListUseCaseに処理を委譲
        pluginListUseCase.getUnmanagedPlugins()

    override suspend fun getEnabledPlugins(): List<PluginData> =
        // PluginListUseCaseに処理を委譲
        pluginListUseCase.getEnabledPlugins()

    override suspend fun getDisabledPlugins(): List<PluginData> =
        // PluginListUseCaseに処理を委譲
        pluginListUseCase.getDisabledPlugins()

    override suspend fun getVersions(plugin: RepositoryPlugin): Either<String, List<String>> {
        // PluginVersionsUseCaseに処理を委譲
        val versions = pluginVersionsUseCase.getVersions(plugin.pluginId)
        return versions
    }

    override suspend fun checkOutdated(plugin: InstalledPlugin): Either<String, OutdatedInfo> =
        // CheckOutdatedUseCaseに処理を委譲
        checkOutdatedUseCase.checkOutdated(plugin.pluginId)

    override suspend fun checkAllOutdated(): Either<String, List<OutdatedInfo>> =
        // CheckOutdatedUseCaseに処理を委譲
        checkOutdatedUseCase.checkAllOutdated()
}