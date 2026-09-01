/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.migration

/**
 * スキーマ版数を1段階だけ引き上げる変換ステップ
 *
 * マイグレーションは「版数を1つずつ上げるチェーン」として適用されるため、
 * 将来 v3 を追加する際はステップを1つ足すだけで済む。
 *
 * 型引数 [T] にはファイル形式ごとの中間表現を与える。
 * JSON は未知キーを落とさないよう `JsonObject` の木を、
 * YAML は kaml で型付きデコードした DTO をそのまま使う。
 *
 * @param T 変換対象の中間表現
 * @property from 適用前の版数
 * @property to 適用後の版数（常に from + 1）
 * @property transform 中間表現を次の版数の形へ変換する純粋関数
 */
data class SchemaMigrationStep<T>(
    val from: Int,
    val to: Int,
    val transform: (T) -> T
)