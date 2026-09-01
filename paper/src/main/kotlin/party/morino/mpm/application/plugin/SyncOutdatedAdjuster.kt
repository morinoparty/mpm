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
 * sync: プラグイン（子）の更新情報を、同期チェーンの根の更新先バージョンに揃える純粋関数
 *
 * sync: プラグインは実際の更新時、自身のリポジトリの最新ではなく親のバージョンに追従する。
 * そのため dry-run / outdated 表示でも「更新先」を追従先の latest とし、
 * needsUpdate もその latest と現在バージョンの比較で判定するように補正する。
 *
 * ## なぜ「直近の親」ではなく「チェーンの根」を見るのか
 * 多段sync（孫 -> 子 -> 親）では、中間ノードの [OutdatedInfo.latestVersion] は
 * 補正前の値、つまり「中間ノード自身のリポジトリの最新」である。
 * sync: を使う理由がまさに「子のリポジトリは親と別に新しい版を出す」ことなので、
 * 直近の親の値をそのまま引くと孫だけが実際には入らない版を追ってしまう。
 * チェーンを根まで遡り、非sync（＝自分でバージョンを決める）ノードの latest に揃える。
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
    // プラグイン名で引けるように索引化（追従先の latest を参照するため）
    val byName = outdated.associateBy { it.pluginName }
    return outdated.map { info ->
        // sync: 指定でなければそのまま
        if (info.pluginName !in syncTargets) return@map info
        // 循環している場合は追従先が定まらないため補正しない
        val root = resolveSyncRoot(info.pluginName, syncTargets) ?: return@map info
        // 根の更新先バージョンが解決できなければそのまま（根のチェックに失敗した場合など）
        val rootLatest = byName[root]?.latestVersion ?: return@map info
        info.copy(
            latestVersion = rootLatest,
            needsUpdate = info.currentVersion != rootLatest
        )
    }
}

/**
 * sync: のチェーンを遡り、自分でバージョンを決めるノード（＝根）を求める
 *
 * この経路は `validateSyncDependencies` を通らない（cron / outdated 表示から直接呼ばれる）ため、
 * 手で編集された mpm.json の循環sync がそのまま流れてくる。無限ループを避けるため、
 * 一度たどったノードを再訪した時点で「解決不能」として打ち切る。
 *
 * @param start 起点となるプラグイン名
 * @param syncTargets 子プラグイン名 -> 同期先（親）プラグイン名 のマップ
 * @return チェーンの根のプラグイン名。循環していて根が存在しない場合はnull
 */
private fun resolveSyncRoot(
    start: String,
    syncTargets: Map<String, String>
): String? {
    // 起点自身も訪問済みに含めることで、自己参照（A -> A）も循環として検出できる
    val visited = mutableSetOf(start)
    var current = start
    while (true) {
        // 同期先が無ければ、そのノードが自分でバージョンを決める根である
        val parent = syncTargets[current] ?: return current
        if (!visited.add(parent)) return null
        current = parent
    }
}