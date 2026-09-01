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

/**
 * sync: の連動更新を、途中のノードの結果に応じて打ち切るための状態
 *
 * `A <- sync:A の B <- sync:B の C` で B がロックや失敗のまま据え置かれた場合、
 * C が追従すべき「B のバージョン」は動いていない。それでも C を更新すると
 * 「親が更新されなければ子も更新されない」という仕様に反し、
 * C だけが B より新しい版になってしまう。
 *
 * そこで「親のバージョンに到達しなかったプラグイン」を記録し、
 * それに sync: している子孫を追従対象から外す。外した子も記録することで、
 * 打ち切りは孫・ひ孫へ推移的に伝播する。
 *
 * 走査が親に近い順（BFS / トポロジカル順）である前提に依存しており、
 * 子を判定する時点で親の結果は必ず確定している。
 */
class SyncFollowBlocklist {
    // 親のバージョンに到達しなかったプラグイン名（挿入順は判定に影響しないがログ向けに保つ）
    private val blockedPlugins = LinkedHashSet<String>()

    /**
     * 親のバージョンに到達しなかったプラグインを記録する
     *
     * ロック・メタデータ破損・インストール失敗・同期先の解決不能のいずれでも呼ぶ。
     *
     * @param pluginName 追従しなかったプラグイン名
     */
    fun block(pluginName: String) {
        blockedPlugins.add(pluginName)
    }

    /**
     * 追従を打ち切るべきかを判定する
     *
     * @param syncTarget 判定対象プラグインの同期先（sync:指定でない場合はnull）
     * @return 打ち切る場合は原因となった同期先の名前、追従してよい場合はnull
     */
    fun blockingTargetOf(syncTarget: String?): String? = syncTarget?.takeIf { it in blockedPlugins }
}