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

package party.morino.mpm.infrastructure.config

/**
 * 直近の config.json 読み込みでデフォルト設定へフォールバックしたかを知るための診断情報
 *
 * ## なぜ ConfigManager 本体と分けるのか
 * onEnable では「読めなくても既定値で起動を続ける」のが正しい振る舞いであり、
 * [party.morino.mpm.api.domain.config.ConfigManager.reload] の戻り値を変えて
 * 公開APIのバイナリ互換性を壊す理由は無い。
 * 一方 `/mpm reload` は利用者が結果を見ている操作であり、実際にはデフォルトへ
 * 差し替わったのに緑の成功表示だけを返すと、設定が無音で失われたのと同じことになる。
 * そこで paper モジュール内だけで参照する診断用のインターフェースとして切り出している。
 */
interface ConfigLoadDiagnostics {
    /**
     * 直近の読み込みでフォールバックが起きた理由
     *
     * フォールバックしていない場合（正常に読み込めた場合、およびファイルが存在せず
     * 既定値を作成した通常の初回起動）はnullになる。
     */
    val lastLoadFailure: String?
}
