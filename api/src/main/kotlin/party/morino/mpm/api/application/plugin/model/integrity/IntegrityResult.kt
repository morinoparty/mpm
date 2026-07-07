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

package party.morino.mpm.api.application.plugin.model.integrity

/**
 * ダウンロードしたプラグインJARの整合性検証の結果
 *
 * どの結果でも、ファイルから計算した [sha256] を保持する。
 * これはtrust-on-first-use（初回信頼）のためにメタデータへ保存され、
 * 次回以降のインストール時に自己検証できるようにする。
 */
sealed class IntegrityResult {
    /** 検証対象ファイルから計算したSHA-256ハッシュ（メタデータ保存用） */
    abstract val sha256: String

    /**
     * 検証成功
     *
     * リモート提供ハッシュ、または保存済みハッシュと一致した状態。
     *
     * @param source 一致に使用したハッシュの出所
     */
    data class Verified(
        override val sha256: String,
        val source: IntegritySource
    ) : IntegrityResult()

    /**
     * ハッシュ不一致
     *
     * 破損・切り詰め・改竄・エラーページの取得などが疑われる状態。
     *
     * @param algorithm 不一致となったハッシュアルゴリズム（例: "SHA-256"）
     * @param expected 期待されたハッシュ（複数artifactの場合はカンマ区切り）
     * @param actual 実際に計算されたハッシュ
     */
    data class Mismatch(
        override val sha256: String,
        val algorithm: String,
        val expected: String,
        val actual: String
    ) : IntegrityResult()

    /**
     * 照合可能なハッシュが存在しない
     *
     * リポジトリがハッシュを提供せず、保存済みハッシュもない状態。
     * 検証はできないが、[sha256] を保存して次回以降の自己検証に備える。
     */
    data class NoReference(
        override val sha256: String
    ) : IntegrityResult()
}