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

package party.morino.mpm.api.application.plugin

import party.morino.mpm.api.application.plugin.model.integrity.IntegrityResult
import party.morino.mpm.api.domain.downloader.model.UrlData
import java.io.File

/**
 * ダウンロードしたプラグインJARの整合性検証を担当するサービス
 *
 * npm/cargo/apt等が備える「インストール時のハッシュ検証」に相当する。
 * リポジトリがAPIで提供するハッシュ（Modrinth: sha1/sha512、Hangar: sha256）や、
 * 以前のインストール時にメタデータへ保存したハッシュと照合し、
 * 破損・改竄されたファイルの黙示的なインストールを防ぐ。
 */
interface IntegrityVerifier {
    /**
     * ダウンロードしたファイルの整合性を検証する
     *
     * 照合の優先順位:
     * 1. リポジトリ提供ハッシュ（sha256 → sha512 → sha1 の順）
     * 2. メタデータに保存済みのsha256（trust-on-first-use）
     * 3. いずれもなければ検証不能（[IntegrityResult.NoReference]）
     *
     * @param file 検証対象のファイル（通常はステージング前のtempファイル）
     * @param urlData リポジトリのURLデータ（リモートハッシュ取得に使用）
     * @param versionName リポジトリ上のバージョン名（リモートハッシュ取得に使用）
     * @param storedSha256 メタデータに保存済みのsha256（なければnull）
     * @param fileNamePattern ダウンロード時と同じファイル/プラットフォーム選択に使用するパターン（オプション）
     * @return 検証結果
     */
    suspend fun verify(
        file: File,
        urlData: UrlData,
        versionName: String,
        storedSha256: String?,
        fileNamePattern: String? = null
    ): IntegrityResult

    /**
     * ファイルのSHA-256ハッシュを計算する
     *
     * インストール済みJARの再検証（`mpm verify`）などで使用する。
     *
     * @param file ハッシュを計算するファイル
     * @return SHA-256ハッシュの16進数小文字文字列
     */
    fun computeSha256(file: File): String
}