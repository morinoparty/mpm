/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
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
 * チャンネルの解決方法が設定されていない場合のみ、呼び出し側は既存の
 * `getLatestVersion` / `getLatestVersionByTag` にフォールバックする。
 * 設定はあるのに該当バージョンが無い場合にフォールバックすると、設定した条件を
 * 無視した結果を「最新」として返すことになるため、そのケースはエラーとして扱う。
 */
object ChannelVersionResolver {
    /**
     * 指定チャンネルの最新バージョンを、リポジトリ設定の [ChannelConfig] で解決する
     *
     * 解決優先順位:
     * 1. `versionMatcher` (regex) でフィルタ
     * 2. `useUpstreamLabel` が `true` の場合はプラットフォーム固有ラベル
     *    （Modrinth `version_type` / GitHub `prerelease`）に委譲
     * 3. どちらも指定されていなければ [ChannelResolution.NotConfigured] を返し、
     *    呼び出し側でフォールバックさせる
     *
     * @param downloaderRepository バージョン取得に使うダウンローダー
     * @param urlData 対象リポジトリのURL情報
     * @param repoConfig リポジトリ設定
     * @param channel 解決したいチャンネル名（"latest" / "release" / "beta" / "alpha"）
     * @return 解決結果。フォールバックしてよいのは [ChannelResolution.NotConfigured] のときだけ
     */
    internal suspend fun resolveLatestInChannel(
        downloaderRepository: DownloaderRepository,
        urlData: UrlData,
        repoConfig: RepositoryConfig,
        channel: String
    ): ChannelResolution {
        val channelConfig =
            repoConfig.channelConfig(channel) ?: return ChannelResolution.NotConfigured

        // (1) versionMatcher が指定されていればregexベースで解決
        val matcherPattern = channelConfig.versionMatcher
        if (matcherPattern != null) {
            val regex =
                runCatching { Regex(matcherPattern) }.getOrElse {
                    return ChannelResolution.UpstreamFailure(
                        "versionMatcher が正規表現として不正です: $matcherPattern"
                    )
                }
            return try {
                val all = downloaderRepository.getAllVersions(urlData)
                // getAllVersionsはプラットフォーム側で新しい順に返す想定。先頭マッチを採用
                val matched = all.firstOrNull { regex.containsMatchIn(it.version) }
                // 該当が無い場合にフィルタ無しの最新へ落とすと、versionMatcher を
                // 設定した意味が無くなる（対象外のバージョンを最新として提示してしまう）
                matched?.let { ChannelResolution.Resolved(it) } ?: ChannelResolution.NoMatch
            } catch (e: Exception) {
                ChannelResolution.UpstreamFailure("バージョン一覧の取得に失敗しました: ${e.message}")
            }
        }

        // (2) useUpstreamLabel が opt-in されていればプラットフォームネイティブ解決に委譲
        if (channelConfig.useUpstreamLabel) {
            return try {
                val resolved =
                    when (channel.lowercase()) {
                        "latest", "release" -> downloaderRepository.getLatestVersion(urlData)
                        else -> downloaderRepository.getLatestVersionByTag(urlData, channel)
                    }
                resolved?.let { ChannelResolution.Resolved(it) } ?: ChannelResolution.NoMatch
            } catch (e: Exception) {
                ChannelResolution.UpstreamFailure("最新バージョンの取得に失敗しました: ${e.message}")
            }
        }

        // (3) 未設定: フォールバックを呼び出し側に任せる
        return ChannelResolution.NotConfigured
    }

    /**
     * `Latest` 相当のバージョンを、チャンネル設定を優先しつつ解決する便利メソッド
     *
     * [repoConfig] が null、あるいはチャンネル設定が未定義の場合のみ、従来どおり
     * [DownloaderRepository.getLatestVersion] にフォールバックする。
     *
     * `latest` チャンネルに解決方法が設定されているのに該当バージョンが無い場合は、
     * フォールバックせず例外を投げる。ここで素の最新へ落とすと、除外したかったバージョンを
     * 「最新」として提示してしまい、ダウングレードを提案する結果になるため。
     *
     * @throws IllegalStateException チャンネル設定に該当するバージョンを解決できなかった場合
     */
    suspend fun resolveLatest(
        downloaderRepository: DownloaderRepository,
        urlData: UrlData,
        repoConfig: RepositoryConfig?
    ): VersionData {
        val resolution =
            repoConfig?.let {
                resolveLatestInChannel(downloaderRepository, urlData, it, "latest")
            } ?: ChannelResolution.NotConfigured

        return when (resolution) {
            is ChannelResolution.Resolved -> resolution.version
            // 解決方法が設定されていないときだけ、プラットフォーム既定へ委ねる
            ChannelResolution.NotConfigured -> downloaderRepository.getLatestVersion(urlData)
            ChannelResolution.NoMatch ->
                error("latest チャンネルの条件に一致するバージョンがありません")
            is ChannelResolution.UpstreamFailure -> error(resolution.message)
        }
    }

    /**
     * 指定タグチャンネルのバージョンを、チャンネル設定を優先しつつ解決する便利メソッド
     *
     * [repoConfig] が null、あるいは該当チャンネル設定が未定義の場合のみ、従来どおり
     * [DownloaderRepository.getLatestVersionByTag] にフォールバックする。
     *
     * 設定があるのに該当バージョンが無い場合は null を返す（呼び出し側が
     * 「そのタグに該当するバージョンが無い」として扱う）。プラットフォーム既定の
     * 解決へは落とさない。
     *
     * @throws IllegalStateException 上流APIの取得に失敗した場合
     */
    suspend fun resolveTag(
        downloaderRepository: DownloaderRepository,
        urlData: UrlData,
        repoConfig: RepositoryConfig?,
        tag: String
    ): VersionData? {
        val resolution =
            repoConfig?.let {
                resolveLatestInChannel(downloaderRepository, urlData, it, tag)
            } ?: ChannelResolution.NotConfigured

        return when (resolution) {
            is ChannelResolution.Resolved -> resolution.version
            ChannelResolution.NotConfigured -> downloaderRepository.getLatestVersionByTag(urlData, tag)
            // 条件に一致しない＝そのタグのバージョンは存在しない。素の解決へは落とさない
            ChannelResolution.NoMatch -> null
            is ChannelResolution.UpstreamFailure -> error(resolution.message)
        }
    }
}