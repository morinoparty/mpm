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

import party.morino.mpm.api.application.model.outdated.OutdatedInfo

/**
 * sync: プラグイン（子）の更新情報を、その同期先（親）の更新先バージョンに揃える純粋関数
 *
 * sync: プラグインは実際の更新時、自身のリポジトリの最新ではなく親のバージョンに追従する。
 * そのため dry-run / outdated 表示でも「更新先」を親の latest とし、
 * needsUpdate も親の latest と現在バージョンの比較で判定するように補正する。
 *
 * バージョン比較は raw 文字列の一致で行う。sync: の子は親と同一バージョンの成果物を配布するため、
 * 同期済みであれば両者の raw は一致する。
 *
 * @param outdated 各プラグインの更新情報（親・子の両方を含む）
 * @param syncTargets 子プラグイン名 -> 同期先（親）プラグイン名 のマップ
 * @return 子の更新先バージョンと needsUpdate を補正した新しいリスト（親や非同期プラグインはそのまま）
 */
fun adjustSyncOutdated(
    outdated: List<OutdatedInfo>,
    syncTargets: Map<String, String>
): List<OutdatedInfo> {
    // プラグイン名で引けるように索引化（親の latest を参照するため）
    val byName = outdated.associateBy { it.pluginName }
    return outdated.map { info ->
        // sync: 指定でなければそのまま
        val parent = syncTargets[info.pluginName] ?: return@map info
        // 親の更新先バージョンが解決できなければそのまま
        val parentLatest = byName[parent]?.latestVersion ?: return@map info
        info.copy(
            latestVersion = parentLatest,
            needsUpdate = info.currentVersion != parentLatest
        )
    }
}