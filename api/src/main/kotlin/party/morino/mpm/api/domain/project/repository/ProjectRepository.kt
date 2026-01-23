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

package party.morino.mpm.api.domain.project.repository

import party.morino.mpm.api.domain.project.model.MpmProject

/**
 * プロジェクトリポジトリのインターフェース
 *
 * mpm.jsonファイルの読み書きを担当する
 * DTOへの変換は実装クラスで行う
 */
interface ProjectRepository {
    /**
     * プロジェクトを取得
     *
     * @return プロジェクト、存在しない場合はnull
     */
    suspend fun find(): MpmProject?

    /**
     * プロジェクトを保存
     *
     * @param project 保存するプロジェクト
     */
    suspend fun save(project: MpmProject)

    /**
     * プロジェクトが存在するかどうかを確認
     *
     * @return 存在する場合はtrue
     */
    suspend fun exists(): Boolean

    /**
     * プロジェクトを削除
     *
     * @return 削除に成功した場合はtrue
     */
    suspend fun delete(): Boolean
}