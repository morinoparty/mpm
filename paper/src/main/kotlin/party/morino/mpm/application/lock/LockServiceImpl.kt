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

package party.morino.mpm.application.lock

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.application.lock.LockService
import party.morino.mpm.api.domain.plugin.model.PluginSpec
import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import party.morino.mpm.api.domain.project.lock.LockEntry
import party.morino.mpm.api.domain.project.lock.LockRepository
import party.morino.mpm.api.domain.project.lock.MpmLock
import party.morino.mpm.api.domain.project.repository.ProjectRepository
import party.morino.mpm.api.shared.error.MpmError
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * [LockService] の実装
 *
 * mpm.jsonの管理下プラグイン一覧と各プラグインのメタデータを突き合わせて
 * ロックファイルを再生成する。
 */
class LockServiceImpl :
    LockService,
    KoinComponent {
    // Koinによる依存性注入
    private val projectRepository: ProjectRepository by inject()
    private val metadataManager: PluginMetadataManager by inject()
    private val lockRepository: LockRepository by inject()

    override suspend fun regenerate(): Either<MpmError, MpmLock> {
        // プロジェクト（mpm.json）を取得。未初期化ならエラー
        val project =
            projectRepository.find()
                ?: return MpmError.ProjectError.NotInitialized.left()

        val now = Instant.now().truncatedTo(ChronoUnit.SECONDS).toString()

        // 管理下（unmanaged以外）のプラグインを名前順に処理して決定的な出力にする
        val entries =
            project.plugins
                .filterValues { it !is PluginSpec.Unmanaged }
                .keys
                .sortedBy { it.value }
                .mapNotNull { pluginName ->
                    // メタデータが読めないプラグインはロックに含めない（不完全なエントリを避ける）
                    val metadata =
                        metadataManager.loadMetadata(pluginName.value).getOrNull()
                            ?: return@mapNotNull null
                    val mpmInfo = metadata.mpmInfo
                    // fileNameが無いプラグインは、add直後にinstallが失敗した等で実インストールが
                    // 完了していない状態のため、ロックには含めない（不完全なエントリを避ける）
                    if (mpmInfo.download.fileName == null) {
                        return@mapNotNull null
                    }
                    // インストール日時は履歴の最新エントリを優先し、無ければ生成時刻を使う
                    val installedAt = mpmInfo.history.lastOrNull()?.installedAt ?: now
                    pluginName.value to
                        LockEntry(
                            version = mpmInfo.version.current,
                            download = mpmInfo.download,
                            repository = mpmInfo.repository,
                            installedAt = installedAt
                        )
                }.toMap()

        val lock =
            MpmLock(
                lockfileVersion = MpmLock.CURRENT_LOCKFILE_VERSION,
                generatedAt = now,
                plugins = entries
            )

        return try {
            lockRepository.save(lock)
            lock.right()
        } catch (e: Exception) {
            MpmError.ProjectError.SaveFailed("ロックファイルの保存に失敗しました: ${e.message}").left()
        }
    }

    override suspend fun find(): MpmLock? = lockRepository.find()
}