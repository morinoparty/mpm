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
 * Hangarのプロジェクト情報
 *
 * 検索API（/projects）とプロジェクト詳細API（/projects/{slug}）の
 * 双方で利用する共通モデル。エンドポイントによって含まれないフィールドが
 * あるため、必須でない項目はnull許容としている。
 * @param name プロジェクト名
 * @param description プロジェクトの説明（未設定の場合はnull）
 * @param namespace 名前空間（owner/slug）。検索結果でslug組み立てに使用
 * @param stats 統計情報（ダウンロード数など）
 * @param settings 設定情報（ライセンス・ホームページなど。詳細取得時に使用）
 */
@Serializable
data class HangarProject(
    val name: String,
    val description: String? = null,
    val namespace: HangarProjectNamespace? = null,
    val stats: HangarProjectStats? = null,
    val settings: HangarProjectSettings? = null
)