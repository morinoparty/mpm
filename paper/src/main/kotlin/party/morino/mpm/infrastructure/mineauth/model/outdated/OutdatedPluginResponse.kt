/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth.model.outdated

import kotlinx.serialization.Serializable
import party.morino.mpm.api.application.model.outdated.OutdatedInfo

/**
 * 更新チェックが成功したプラグイン1件分の情報レスポンス
 *
 * @property name プラグイン名
 * @property currentVersion 現在のバージョン
 * @property latestVersion 上流リポジトリの最新バージョン（mpm.json の指定に関わらない）
 * @property targetVersion mpm.json の指定が指す更新先バージョン（固定バージョンならその固定値）
 * @property needsUpdate 更新が必要かどうか（現在のバージョンと targetVersion を正規化済みで比較した結果）
 * @property hasNewerUpstream latestVersion が targetVersion と異なるか（固定バージョンが上流に置いていかれている目印）
 */
@Serializable
data class OutdatedPluginResponse(
    val name: String,
    val currentVersion: String,
    val latestVersion: String,
    val targetVersion: String,
    val needsUpdate: Boolean,
    val hasNewerUpstream: Boolean
) {
    companion object {
        /**
         * OutdatedInfoから変換する
         */
        fun from(info: OutdatedInfo): OutdatedPluginResponse =
            OutdatedPluginResponse(
                name = info.pluginName,
                currentVersion = info.currentVersion,
                latestVersion = info.latestVersion,
                targetVersion = info.targetVersion,
                needsUpdate = info.needsUpdate,
                hasNewerUpstream = info.hasNewerUpstream
            )
    }
}