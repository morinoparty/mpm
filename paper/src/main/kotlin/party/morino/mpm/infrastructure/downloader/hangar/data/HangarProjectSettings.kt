/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.downloader.hangar.data

import kotlinx.serialization.Serializable

/**
 * Hangarプロジェクトの設定情報
 *
 * ホームページは独立したフィールドではなく、リンクグループ（links）の中に
 * name=="Homepage" のリンクとして含まれる。
 * @param license ライセンス情報（プロジェクト詳細のライセンス表示に使用）
 * @param links リンクグループ一覧（ホームページ等の抽出に使用）
 */
@Serializable
data class HangarProjectSettings(
    val license: HangarProjectLicense? = null,
    val links: List<HangarProjectLinkGroup> = emptyList()
)