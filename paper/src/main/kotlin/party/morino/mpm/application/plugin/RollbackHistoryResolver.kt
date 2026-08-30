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

package party.morino.mpm.application.plugin

import party.morino.mpm.api.domain.plugin.dto.version.HistoryEntryDto

/**
 * インストール履歴から「切り戻し先バージョン」を解決する純粋関数
 *
 * `mpm rollback`（バージョン省略時）は必ず**より過去**へ進む、という不変条件を満たすための規則を実装する。
 *
 * 規則:
 * 1. 履歴の中で現在バージョンが**最初に現れた位置**を探す。
 * 2. その位置より前の最後のエントリを切り戻し先とする（現在バージョンより前に導入されていたバージョン）。
 * 3. 現在バージョンが履歴に存在しない場合は、末尾から遡って現在と異なる最初のエントリを採用する。
 * 4. 該当するエントリが無い場合（履歴が空・1件のみ・全て同じバージョン・最初のエントリが現在バージョン）は
 *    null を返し、呼び出し側が「切り戻せる過去バージョンがありません」として扱う。
 *
 * 「最初に現れた位置」を基準にするのは、rollback 自身が履歴に `action=rollback` のエントリを追記するため。
 * 末尾から単純に遡ると、直前の rollback で離れたばかりの新しいバージョンを再び選んでしまい、
 * 2つのバージョンを往復するだけでそれ以上過去へ辿れなくなる。
 *
 * この規則は保守的であり、例えば履歴 `[3.0.0, 1.0.0, 2.0.0, 3.0.0]`（現在 3.0.0）では
 * 最初の出現が先頭のため切り戻し先なしと判定する（意図せず新しいバージョンへ「前進」しないことを優先する）。
 *
 * 履歴に記録されるのは正規化済みバージョンのため、[currentNormalized] も正規化済みを渡し、
 * 戻り値も正規化済みバージョンとなる。
 *
 * @param history インストール履歴（古い順）
 * @param currentNormalized 現在インストールされているバージョン（正規化済み）
 * @return 切り戻し先バージョン（正規化済み）。見つからない場合は null
 */
fun resolveRollbackTargetVersion(
    history: List<HistoryEntryDto>,
    currentNormalized: String
): String? {
    // 現在バージョンが履歴に初めて現れた位置
    val firstCurrentIndex = history.indexOfFirst { it.version == currentNormalized }

    // 履歴に現在バージョンが無い場合は、末尾から遡って現在と異なる最初のエントリを採用する
    if (firstCurrentIndex < 0) {
        return history.lastOrNull { it.version != currentNormalized }?.version
    }

    // 現在バージョンが初めて現れた位置より前のエントリだけが「より過去」である。
    // 定義より、その範囲に現在バージョンは含まれないため、末尾のエントリをそのまま採用できる。
    return history.subList(0, firstCurrentIndex).lastOrNull()?.version
}