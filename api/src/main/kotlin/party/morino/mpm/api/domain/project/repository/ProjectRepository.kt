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

import arrow.core.Either
import party.morino.mpm.api.domain.project.model.MpmProject
import party.morino.mpm.api.shared.error.MpmError

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
     * プロジェクトを取得（エラー情報付き）
     *
     * find()と異なり、ファイル未存在とパースエラーを区別する
     *
     * @return Right(project) パース成功時、Left(ConfigNotFound) ファイル未存在時、Left(ConfigParseError) パース失敗時
     */
    suspend fun findOrError(): Either<MpmError, MpmProject>

    /**
     * プロジェクトを保存
     *
     * @param project 保存するプロジェクト
     */
    suspend fun save(project: MpmProject)

    /**
     * mpm.json を保存してよいかを事前に判定する（副作用なし）
     *
     * ディスク上の mpm.json が現行スキーマ版数より新しい場合、[save] は必ず失敗する。
     * ところが `uninstall` のように「JARを削除してから mpm.json を保存する」経路では、
     * 保存地点で初めて拒否されるとJARだけが消えて mpm.json にはプラグインが残る、という
     * 中途半端な状態になってしまう。
     *
     * そのため呼び出し側は「まだ何も壊していない段階」でこれを呼び、
     * 破壊的操作に入る前に中止できるようにする。
     * [save] 側のガードは最終防御として残してあり、この事前判定は早期中断のためのものである。
     *
     * @return 保存してよい場合はUnit、未来版数のため中止すべき場合はその理由
     */
    suspend fun ensureSavable(): Either<String, Unit>

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