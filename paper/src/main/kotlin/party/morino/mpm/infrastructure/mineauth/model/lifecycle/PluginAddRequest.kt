/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth.model.lifecycle

import kotlinx.serialization.Serializable

/**
 * プラグイン追加リクエストボディ
 *
 * mpm.json へのエントリ追加とメタデータ作成のみを行う `mpm add` 相当の操作に対応する。
 * jar の配置は行わないため、続けて `POST /plugins/{name}/install` を呼ぶ必要がある。
 *
 * @property version バージョン指定。`latest` / `sync:PluginName` / `tag:beta` / 固定バージョンを受け付け、
 *   コマンド版と同じく VersionSpecifierParser.parse で解釈される（既定値: `latest`）
 */
@Serializable
data class PluginAddRequest(
    val version: String = "latest"
)