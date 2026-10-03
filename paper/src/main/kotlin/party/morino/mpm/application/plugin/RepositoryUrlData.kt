/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

import party.morino.mpm.api.domain.downloader.model.UrlData
import party.morino.mpm.api.domain.repository.RepositoryConfig

/**
 * RepositoryConfigからダウンローダー用のUrlDataを生成する
 *
 * @return 対応するUrlData（未対応のタイプや不正なID形式の場合はnull）
 */
internal fun RepositoryConfig.toUrlData(): UrlData? =
    when (type.lowercase()) {
        "github" ->
            repositoryId
                .split("/")
                .takeIf { it.size == 2 }
                ?.let { (owner, repository) -> UrlData.GithubUrlData(owner, repository) }
        "modrinth" -> UrlData.ModrinthUrlData(repositoryId)
        "spigotmc" -> UrlData.SpigotMcUrlData(repositoryId)
        "hangar" ->
            repositoryId
                .split("/")
                .let { parts ->
                    // Hangar形式: "owner/project"（ownerを省略したslug単体も許容する）
                    when (parts.size) {
                        2 -> UrlData.HangarUrlData(owner = parts[0], projectName = parts[1])
                        1 -> UrlData.HangarUrlData(owner = "", projectName = parts[0])
                        else -> null
                    }
                }
        else -> null
    }