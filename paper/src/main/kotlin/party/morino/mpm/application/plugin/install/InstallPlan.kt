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

/**
 * 一括インストールで「何を入れるか」を決めた計画
 *
 * @param pluginsToInstall インストールを実行するプラグイン名（入力と同じトポロジカル順）
 * @param lockedSkipped 更新が必要だがロック中のため据え置いたプラグイン名
 * @param resolvedVersions 「この実行の終了時点で各プラグインが居るはずのバージョン」。
 *   sync: の子孫がインストール対象かどうかを判定するために使う。
 *   確定できないもの（latest / tag: や、それに追従する子孫）については
 *   現時点で分かる最良の値（ディスク上のバージョン）が入る
 */
data class InstallPlan(
    val pluginsToInstall: List<String>,
    val lockedSkipped: List<String>,
    val resolvedVersions: Map<String, String>
)