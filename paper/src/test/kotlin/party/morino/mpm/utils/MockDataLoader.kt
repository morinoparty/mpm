/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils

import java.io.File

/**
 * mockデータを読み込むユーティリティオブジェクト
 */
object MockDataLoader {
    /**
     * resourcesディレクトリからmockデータを読み込む
     * @param path resourcesディレクトリからの相対パス
     * @return ファイルの内容
     */
    fun loadMockData(path: String): String {
        // resourcesディレクトリからファイルを読み込む
        val resource =
            this::class.java.classLoader.getResource(path)
                ?: throw IllegalArgumentException("Mock data file not found: $path")
        return File(resource.toURI()).readText()
    }

    /**
     * GitHub APIのmockデータを読み込む
     */
    object Github {
        /**
         * /repos/{owner}/{repo}/releases/latest のレスポンスを取得
         */
        fun getLatestRelease(): String = loadMockData("mock/http/github/releases_latest.json")

        /**
         * /repos/{owner}/{repo}/releases/{releaseId}/assets のレスポンスを取得
         */
        fun getReleaseAssets(): String = loadMockData("mock/http/github/releases_assets.json")
    }

    /**
     * Spigot APIのmockデータを読み込む
     */
    object Spigot {
        /**
         * /v2/resources/{resourceId} のレスポンスを取得
         */
        fun getResourceDetails(): String = loadMockData("mock/http/spigot/resource_details.json")

        /**
         * /v2/resources/{resourceId}/versions?sort=-name&size=1 のレスポンスを取得
         */
        fun getLatestVersion(): String = loadMockData("mock/http/spigot/versions_latest.json")
    }

    /**
     * Modrinth APIのmockデータを読み込む
     */
    object Modrinth {
        /**
         * https://api.modrinth.com/v2/project/plasmo-voice のレスポンスを取得
         */
        fun getProjectDetails(): String = loadMockData("mock/http/modrinth/project-info.json")

        /**
         * https://api.modrinth.com/v2/project/plasmo-voice/?loaders=%5B%22paper%22%2C+%22spigot%22%5D のレスポンスを取得
         */
        fun getProjectVersions(): String = loadMockData("mock/http/modrinth/version.json")

        /**
         * https://api.modrinth.com/v2/version/{versionId} のレスポンスを取得
         */
        fun getVersionDetail(): String = loadMockData("mock/http/modrinth/version-detail.json")
    }
}