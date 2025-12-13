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

package party.morino.mpm.api.core.plugin

import arrow.core.Either

/**
 * プロジェクト管理インターフェース
 * プロジェクトの初期化を担当
 */
interface ProjectManager {
    /**
     * プロジェクトを初期化する
     * rootDirectory/mpm.jsonを生成し、pluginsディレクトリ内のすべてのプラグインをunmanagedとして追加する
     *
     * @param projectName プロジェクト名
     * @param overwrite 既存のmpm.jsonを上書きするかどうか
     * @return 成功時はUnit、失敗時はエラーメッセージ
     */
    suspend fun initialize(
        projectName: String,
        overwrite: Boolean = false
    ): Either<String, Unit>
}