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
     * 指定チャンネルの最新バージョンを、リポジトリ設定の [ChannelConfig] で解決する
     *
     * 解決優先順位:
     * 1. `versionMatcher` (regex) でフィルタ
     * 2. `useUpstreamLabel` が `true` の場合はプラットフォーム固有ラベル
     *    （Modrinth `version_type` / GitHub `prerelease`）に委譲
     * 3. どちらも指定されていなければ null を返し、呼び出し側でフォールバックさせる
     *
     * @param downloaderRepository バージョン取得に使うダウンローダー
     * @param urlData 対象リポジトリのURL情報
     * @param repoConfig リポジトリ設定
     * @param channel 解決したいチャンネル名（"latest" / "release" / "beta" / "alpha"）
     * @return 解決されたバージョン。設定なし・マッチなし・APIエラー時はnull
     */
    suspend fun resolveLatestInChannel(
        downloaderRepository: DownloaderRepository,
        urlData: UrlData,
        repoConfig: RepositoryConfig,
        channel: String
    ): VersionData? {
        val channelConfig = repoConfig.channelConfig(channel) ?: return null

        // (1) versionMatcher が指定されていればregexベースで解決
        val matcherPattern = channelConfig.versionMatcher
        if (matcherPattern != null) {
            val regex = runCatching { Regex(matcherPattern) }.getOrElse { return null }
            return try {
                val all = downloaderRepository.getAllVersions(urlData)
                // getAllVersionsはプラットフォーム側で新しい順に返す想定。先頭マッチを採用
                all.firstOrNull { regex.containsMatchIn(it.version) }
            } catch (_: Exception) {
                null
            }
        }

        // (2) useUpstreamLabel が opt-in されていればプラットフォームネイティブ解決に委譲
        if (channelConfig.useUpstreamLabel) {
            return try {
                when (channel.lowercase()) {
                    "latest", "release" -> downloaderRepository.getLatestVersion(urlData)
                    else -> downloaderRepository.getLatestVersionByTag(urlData, channel)
                }
            } catch (_: Exception) {
                null
            }
        }

        // (3) 未設定: フォールバックを呼び出し側に任せる
        return null
    }

    /**
     * `Latest` 相当のバージョンを、チャンネル設定を優先しつつ解決する便利メソッド
     *
     * [repoConfig] が null、あるいはチャンネル設定が未定義の場合は、従来どおり
     * [DownloaderRepository.getLatestVersion] にフォールバックする。
     */
    suspend fun resolveLatest(
        downloaderRepository: DownloaderRepository,
        urlData: UrlData,
        repoConfig: RepositoryConfig?
    ): VersionData {
        val fromChannel =
            repoConfig?.let {
                resolveLatestInChannel(downloaderRepository, urlData, it, "latest")
            }
        return fromChannel ?: downloaderRepository.getLatestVersion(urlData)
    }

    /**
     * 指定タグチャンネルのバージョンを、チャンネル設定を優先しつつ解決する便利メソッド
     *
     * [repoConfig] が null、あるいは該当チャンネル設定が未定義の場合は、従来どおり
     * [DownloaderRepository.getLatestVersionByTag] にフォールバックする。
     */
    suspend fun resolveTag(
        downloaderRepository: DownloaderRepository,
        urlData: UrlData,
        repoConfig: RepositoryConfig?,
        tag: String
    ): VersionData? {
        val fromChannel =
            repoConfig?.let {
                resolveLatestInChannel(downloaderRepository, urlData, it, tag)
            }
        return fromChannel ?: downloaderRepository.getLatestVersionByTag(urlData, tag)
    }
}