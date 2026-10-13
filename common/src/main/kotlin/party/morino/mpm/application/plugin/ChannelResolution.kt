/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.plugin

import party.morino.mpm.api.domain.downloader.model.VersionData

/**
 * チャンネル設定によるバージョン解決の結果
 *
 * 以前は解決できなかった場合をすべて null で表していたため、
 * 「チャンネル設定が無い」と「設定はあるが該当バージョンが無い」を区別できなかった。
 * その結果、`versionMatcher` を設定していても該当が無いとフィルタ無しの最新へ
 * 黙って落ちてしまい、対象外のバージョンを「最新」として提示していた
 * （例: Vault に `^\d+\.\d+\.\d+$` を設定していたのに `Version 1.6.6` が最新と判定された）。
 */
internal sealed interface ChannelResolution {
    /** チャンネル設定に従ってバージョンを解決できた */
    data class Resolved(
        val version: VersionData
    ) : ChannelResolution

    /**
     * このチャンネルの解決方法が設定されていない
     *
     * 呼び出し側がプラットフォーム既定の解決へフォールバックしてよい唯一のケース。
     */
    data object NotConfigured : ChannelResolution

    /**
     * 設定はあるが、該当するバージョンが存在しなかった
     *
     * フォールバックしてはいけない。設定した条件を無視した結果を返すことになるため。
     */
    data object NoMatch : ChannelResolution

    /**
     * 上流APIの取得に失敗した
     *
     * 一時障害と「該当なし」を取り違えないよう区別する。こちらもフォールバックしない。
     */
    data class UpstreamFailure(
        val message: String
    ) : ChannelResolution
}