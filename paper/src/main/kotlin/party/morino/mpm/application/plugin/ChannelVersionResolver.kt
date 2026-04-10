/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

import party.morino.mpm.api.domain.downloader.DownloaderRepository
import party.morino.mpm.api.domain.downloader.model.UrlData
import party.morino.mpm.api.domain.downloader.model.VersionData
import party.morino.mpm.api.domain.repository.RepositoryConfig

/**
 * リポジトリファイルに定義された `latest` / `beta` / `alpha` チャンネルの
 * `versionMatcher` を使ってバージョンを解決するヘルパー。
 *
 * プラットフォーム固有のチャンネル分類（Modrinthの version_type、GitHubの prerelease）
 * ではなく、リポジトリファイル側で定義した正規表現でチャンネルを識別したい場合に使用する。
 *
 * マッチャーが設定されていない、もしくはマッチするバージョンが存在しない場合はnullを返し、
 * 呼び出し側は既存の `getLatestVersion` / `getLatestVersionByTag` にフォールバックする。
 */
object ChannelVersionResolver {
    /**
     * 指定チャンネルの最新バージョンを、リポジトリ設定の `versionMatcher` で解決する
     *
     * @param downloaderRepository バージョン取得に使うダウンローダー
     * @param urlData 対象リポジトリのURL情報
     * @param repoConfig リポジトリ設定（`latest` / `beta` / `alpha` のいずれかに
     *   `versionMatcher` が入っている想定）
     * @param channel 解決したいチャンネル名（"latest" / "release" / "beta" / "alpha"）
     * @return マッチャーでフィルタした最新バージョン。マッチャー未定義、マッチなし、
     *   またはAPIエラーの場合はnull
     */
    suspend fun resolveLatestInChannel(
        downloaderRepository: DownloaderRepository,
        urlData: UrlData,
        repoConfig: RepositoryConfig,
        channel: String
    ): VersionData? {
        // 対応するマッチャー正規表現を取得（未指定ならフォールバック）
        val matcherPattern = repoConfig.channelVersionMatcher(channel) ?: return null

        // 正規表現のコンパイルに失敗した場合もフォールバック（ユーザー入力に起因するため）
        val regex = runCatching { Regex(matcherPattern) }.getOrElse { return null }

        return try {
            val all = downloaderRepository.getAllVersions(urlData)
            // getAllVersionsはプラットフォーム側で新しい順に返す想定。先頭マッチを採用
            all.firstOrNull { regex.containsMatchIn(it.version) }
        } catch (_: Exception) {
            null
        }
    }
}
