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

package party.morino.mpm.application.plugin.install

import party.morino.mpm.api.domain.plugin.model.VersionSpecifierParser

/**
 * 一括インストール（`mpm install`）で「何を入れるか」を決める純粋関数
 *
 * ## なぜ計画を分けるのか
 * 判定に使うのは「ディスク上の現在バージョン」ではなく
 * **「この実行の終了時点でそのプラグインが居るはずのバージョン」** である。
 * 親が更新される予定なのにディスク上の旧バージョンで子を判定すると、
 * `sync:` の子が「もう同期済み」と誤判定されて対象から漏れ、
 * `mpm install` を2回実行しないと多段syncが収束しなくなる。
 *
 * 例: `A: "2.0.0"`（現在 1.0.0） / `B: "sync:A"`（現在 1.0.0） / `C: "sync:B"`（現在 1.0.0）
 * のとき、A の予定バージョン 2.0.0 を記録することで B が、B の 2.0.0 によって C が
 * それぞれ対象に入り、1回のインストールで A・B・C が揃う。
 *
 * ## 動的指定（latest / tag:）の扱い
 * latest / tag: は計画時点では入るバージョンが原理的に分からない。
 * そこで「バージョン不明」として印を付け、それに追従する子孫は
 * **保守的にインストール対象へ倒す**（実際のバージョンは親のインストール後に確定する）。
 * 判定を諦めて対象から外すと収束しなくなるため、安全側は「入れる」方である。
 * 親が結局同じバージョンだった場合に無駄な再取得をしないための
 * 「同期済みなら何もしない」判定は、実際のバージョンが分かる実行側で行う。
 *
 * ## lock の扱い
 * lock は唯一の拒否権なので、ロック中のプラグインは決してインストール対象にしない。
 * 予定バージョンにはディスク上の実インストール版を記録するため、
 * その子は「据え置かれた親のバージョン」と比較され、不要な追従が起きない。
 * また据え置きが確定している以上、動的指定や追従先不明といった
 * 「分からないから入れる」側の判定も適用しない。適用すると、実際には何も変わらないのに
 * 毎回ロックによるスキップとして報告されてしまうためである。
 *
 * @param candidates トポロジカルソート済み（親が先）のインストール候補。
 *   `unmanaged` のプラグインは呼び出し側で除外しておくこと
 * @return インストール対象・ロックによる据え置き・予定バージョンをまとめた計画
 */
fun planInstallTargets(candidates: List<InstallCandidate>): InstallPlan {
    // この実行の終了時点で各プラグインが居るはずのバージョン
    val resolvedVersions = LinkedHashMap<String, String>()

    // この実行で入るバージョンが計画時点では確定しないプラグイン
    // （latest / tag: 自身と、それに追従する子孫）
    val versionUnknown = mutableSetOf<String>()

    val pluginsToInstall = mutableListOf<String>()
    val lockedSkipped = mutableListOf<String>()

    for (candidate in candidates) {
        val syncTarget = VersionSpecifierParser.extractSyncTarget(candidate.expectedVersion)
        val isDynamic = VersionSpecifierParser.isDynamic(candidate.expectedVersion)

        // 同期先の予定バージョンを引く。トポロジカル順なので親は必ず処理済みである。
        // 引けない場合（同期先が管理外など）は指定文字列のままとし、後段のインストールで失敗させる
        val expectedVersion =
            if (syncTarget != null) {
                resolvedVersions[syncTarget] ?: candidate.expectedVersion
            } else {
                candidate.expectedVersion
            }

        // 同期先のバージョンが確定していない（＝親が latest / tag: か、その子孫）
        val syncTargetUnknown = syncTarget != null && syncTarget in versionUnknown

        // ロック中のプラグインは、追従先が不明でも据え置かれることが確定している。
        // 保守的な「不明なら入れる」を適用すると、実際には何も変わらないのに
        // 毎回ロックによるスキップとして報告されてしまうため、素直な比較のみで判定する
        val needsInstall =
            when {
                // メタデータが無い / 読めない場合はインストールが必要
                candidate.installedVersion == null -> true
                // latest / tag: は解決のたびにバージョンが変わりうるため常に委譲する（#283）。
                // ただしロック中は据え置きが確定しており、実際に版が動くことはないので対象外にする。
                // ここで無条件に true とすると、何も変わらないのに毎回ロックによるスキップとして
                // 報告され、固定バージョン指定（版が一致すれば報告されない）と挙動が食い違う
                isDynamic -> !candidate.locked
                // 追従先の着地バージョンが不明なうちは、取りこぼさないよう対象に入れる
                syncTargetUnknown && !candidate.locked -> true
                else -> candidate.installedVersion != expectedVersion
            }

        if (candidate.locked) {
            // lock は唯一の拒否権。更新が必要でも入れず、据え置きとして報告する
            if (needsInstall) lockedSkipped.add(candidate.pluginName)
            // 据え置かれる以上、この実行の終了時点でもディスク上のバージョンのままである
            resolvedVersions[candidate.pluginName] = candidate.installedVersion ?: expectedVersion
            continue
        }

        if (!needsInstall) {
            // 触らないので、ディスク上のバージョンがそのまま予定バージョンになる
            resolvedVersions[candidate.pluginName] = candidate.installedVersion ?: expectedVersion
            continue
        }

        pluginsToInstall.add(candidate.pluginName)
        if (isDynamic || syncTargetUnknown) {
            // 着地するバージョンが計画時点では分からない。子孫も保守的に対象へ入れるため印を付ける
            versionUnknown.add(candidate.pluginName)
            resolvedVersions[candidate.pluginName] = candidate.installedVersion ?: expectedVersion
        } else {
            // 固定バージョン、または確定した親に追従する sync: は、着地バージョンが分かる。
            // これを記録することで sync: の子孫が正しく対象に入る（多段syncの1回収束）
            resolvedVersions[candidate.pluginName] = expectedVersion
        }
    }

    return InstallPlan(
        pluginsToInstall = pluginsToInstall,
        lockedSkipped = lockedSkipped,
        resolvedVersions = resolvedVersions
    )
}