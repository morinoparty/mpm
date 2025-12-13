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
import party.morino.mpm.api.core.plugin.ProjectManager
import party.morino.mpm.core.plugin.usecase.InitUseCaseImpl

/**
 * ProjectManagerの実装クラス
 * プロジェクトの初期化を担当
 */
class ProjectManagerImpl : ProjectManager {
    // UseCaseImplを直接インスタンス化（KoinComponentなので、内部でRepository等をinject()できる）
    private val initUseCase = InitUseCaseImpl()

    override suspend fun initialize(
        projectName: String,
        overwrite: Boolean
    ): Either<String, Unit> =
        // InitUseCaseに処理を委譲
        initUseCase.initialize(projectName, overwrite)
}