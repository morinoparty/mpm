/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import party.morino.mpm.api.domain.downloader.DownloaderRepository
import party.morino.mpm.api.domain.downloader.model.UrlData
import party.morino.mpm.api.domain.downloader.model.VersionData
import party.morino.mpm.api.domain.plugin.model.VersionDetail
import party.morino.mpm.api.shared.error.MpmError

/**
 * バージョン指定文字列を、リポジトリ上の実在するバージョンへ解決するヘルパー。
 *
 * mpm.json や web console から渡ってくる文字列は、リポジトリ上の実バージョン名（raw）とは
 * 限らない。GitHub のようにタグへ `v` を付ける運用のリポジトリでは、実タグが `v0.3.9` でも
 * 利用者は正規化表記の `0.3.9` を書くことがあり、その文字列をそのまま
 * `/releases/tags/` へ渡すと 404 になる。
 *
 * そのため解決は2段構えにしてある。
 * 1. まず指定文字列を実バージョン名とみなして [DownloaderRepository.getVersionByName] を引く。
 *    `mpm pin` などが書き込む raw タグはこの1リクエストで解決でき、一覧取得の往復を払わずに済む。
 * 2. 見つからない場合のみ全バージョンを取得し、[VersionDetail.normalizeWithPattern] で
 *    正規化した表記どうしを突き合わせる。raw タグがどこにも残っていない指定
 *    （利用者が手で書いた `"0.3.9"` など）を救えるのはこの経路だけ。
 *
 * `v` 接頭辞のような特定の命名規則をハードコードしないのは、`release-0.3.9` のような
 * 別の命名にも同じ問題があり、正規化パターンによる突き合わせのほうが一般的に効くため。
 */
object VersionNameResolver {
    /**
     * 要求されたバージョン文字列をリポジトリ上の [VersionData] へ解決する
     *
     * ダウンロードには `downloadId` が必要になるため、解決したバージョン名だけではなく
     * [VersionData] をそのまま返す。
     *
     * 上流の一時障害（一覧取得の失敗）と、指定文字列が存在しないことは別の失敗として扱う。
     * 前者を [MpmError.PluginError.VersionResolutionFailed] にしてしまうと、
     * 単なる障害を「その指定が間違っている」と利用者へ伝えてしまうため。
     *
     * @param downloaderRepository バージョン取得に使うダウンローダー
     * @param urlData 対象リポジトリのURL情報
     * @param pluginName 対象プラグイン名（エラーメッセージに使用）
     * @param requestedVersion 要求されたバージョン文字列（raw タグ / 正規化表記のどちらでも可）
     * @param versionPattern バージョン正規化に使う正規表現（nullの場合はデフォルトsemverパターン）
     * @return 解決された [VersionData]
     */
    suspend fun resolve(
        downloaderRepository: DownloaderRepository,
        urlData: UrlData,
        pluginName: String,
        requestedVersion: String,
        versionPattern: String? = null
    ): Either<MpmError, VersionData> {
        // (1) まずはリポジトリ上のバージョン名としてそのまま解決を試みる
        val exactMatch =
            try {
                downloaderRepository.getVersionByName(urlData, requestedVersion)
            } catch (e: Exception) {
                // 実バージョン名として存在しないだけの可能性があるため、ここでは失敗としない。
                // ダウンローダー側が404と5xxを区別できる型付き例外を投げないため、
                // 現状はすべての失敗を「見つからなかった」とみなして次の段へ進む
                null
            }
        if (exactMatch != null) return exactMatch.right()

        // (2) 見つからない場合は正規化済みバージョンとみなし、全バージョンを正規化して突き合わせる
        val requestedNormalized = VersionDetail.normalizeWithPattern(requestedVersion, versionPattern)
        val candidates =
            try {
                downloaderRepository.getAllVersions(urlData)
            } catch (e: Exception) {
                // 上流リポジトリの一時障害はクライアントの指定ミスと区別する（HTTPでは503を返す）
                return MpmError.PluginError
                    .UpstreamUnavailable(
                        pluginName,
                        "バージョン一覧の取得に失敗しました: ${e.message}"
                    ).left()
            }

        // 完全一致を先に見るのは、正規化すると同じ表記になる別タグが混在していても
        // 指定どおりのバージョンを優先して選ぶため
        return candidates
            .firstOrNull { candidate ->
                candidate.version == requestedVersion ||
                    VersionDetail.normalizeWithPattern(candidate.version, versionPattern) == requestedNormalized
            }?.right()
            ?: MpmError.PluginError
                .VersionResolutionFailed(
                    pluginName,
                    "バージョン '$requestedVersion' はリポジトリに存在しません"
                ).left()
    }
}