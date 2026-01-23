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

package party.morino.mpm.api.application.project

import arrow.core.Either
import party.morino.mpm.api.domain.project.model.MpmProject
import party.morino.mpm.api.shared.error.MpmError

/**
 * プロジェクト管理サービス
 *
 * MPMプロジェクトの初期化・設定管理を担当する
 * 薄いファサードとして機能し、オーケストレーションのみを行う
 */
interface ProjectService {
    /**
     * プロジェクトを初期化する
     *
     * mpm.jsonファイルを作成し、必要なディレクトリ構造を整備する
     *
     * @param projectName プロジェクト名
     * @return 初期化されたプロジェクト
     */
    suspend fun init(projectName: String): Either<MpmError, MpmProject>

    /**
     * 現在のプロジェクトを取得する
     *
     * @return プロジェクト情報（初期化されていない場合はnull）
     */
    suspend fun getProject(): MpmProject?

    /**
     * プロジェクトを保存する
     *
     * @param project プロジェクト
     * @return 成功時はUnit
     */
    suspend fun save(project: MpmProject): Either<MpmError, Unit>

    /**
     * プロジェクトが初期化されているかどうかを確認する
     *
     * @return 初期化されている場合はtrue
     */
    suspend fun isInitialized(): Boolean
}