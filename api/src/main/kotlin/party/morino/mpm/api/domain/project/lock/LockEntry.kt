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

package party.morino.mpm.api.domain.project.lock

import kotlinx.serialization.Serializable
import party.morino.mpm.api.domain.plugin.dto.MetadataDownloadInfoDto
import party.morino.mpm.api.domain.plugin.dto.RepositoryInfo
import party.morino.mpm.api.domain.plugin.dto.version.VersionDetailDto

/**
 * ロックファイル（mpm-lock.yaml）の1プラグイン分のエントリ
 *
 * 実際にインストールされたプラグインの正確なバージョン・ダウンロード情報を記録し、
 * 別環境での再現可能なインストールを可能にする。フィールド構成はmetadataの
 * 各yamlファイルのサブセットで、既存のDTOを再利用する。
 *
 * @param version バージョン情報（raw/normalized）
 * @param download ダウンロード情報（url/downloadId/fileName/sha256）
 * @param repository 取得元リポジトリ（type/id）
 * @param installedAt インストール日時（ISO 8601形式）
 */
@Serializable
data class LockEntry(
    val version: VersionDetailDto,
    val download: MetadataDownloadInfoDto,
    val repository: RepositoryInfo,
    val installedAt: String
)