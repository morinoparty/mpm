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

package party.morino.mpm.api.application.health

import arrow.core.Either
import party.morino.mpm.api.shared.error.MpmError

/**
 * サーバーのプラグイン管理状態を一括診断するサービス（`mpm doctor`）
 *
 * 依存関係・整合性・更新・ロック・管理外などの既存チェックを集約し、
 * 1回の実行でサーバーの健全性を俯瞰できるようにする。
 */
interface DoctorService {
    /**
     * 各種チェックを実行して診断結果を返す
     *
     * 個別チェックの失敗は [DoctorReport.warnings] に集約し、可能な限り全体の診断を継続する。
     * プロジェクトが未初期化などで診断自体が行えない場合のみエラーを返す。
     *
     * @return 診断結果
     */
    suspend fun diagnose(): Either<MpmError, DoctorReport>
}