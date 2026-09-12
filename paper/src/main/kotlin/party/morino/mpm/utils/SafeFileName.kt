/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.io.File

/**
 * リポジトリ由来の文字列をファイル名として扱う前に検証する
 *
 * JARの配置先ファイル名は `fileNameTemplate`（リポジトリ定義の値）とプラグイン名から
 * 組み立てられるため、`../` を含む値を素通しすると plugins/ の外へ書き込めてしまう。
 * 中央リポジトリの定義はレビューされているが、委譲リポジトリでは第三者が値を供給しうるため、
 * 「単一のパスセグメントであること」をここで強制する。
 *
 * 同じ理由で RemoteRepositorySource もプラグイン名を英数字・ハイフン・アンダースコアに
 * 制限しているが、あちらはURLパス用でファイル名には適用されない。
 */
internal object SafeFileName {
    // パス区切りとして解釈されうる文字（Windowsのドライブ指定 `C:foo` も弾く）
    private val PATH_SEPARATORS = charArrayOf('/', '\\', ':')

    /**
     * ファイル名が単一のパスセグメントとして安全かを検証する
     *
     * @param fileName 検証対象のファイル名
     * @return 安全なら同じ文字列、危険なら理由
     */
    fun validate(fileName: String): Either<String, String> {
        if (fileName.isBlank()) {
            return "ファイル名が空です".left()
        }
        // NUL や制御文字はファイルシステムAPIの解釈を狂わせる
        if (fileName.any { it.isISOControl() }) {
            return "ファイル名に制御文字が含まれています".left()
        }
        // ディレクトリを跨ぐ指定を拒否する（パストラバーサルの本丸）
        if (fileName.any { it in PATH_SEPARATORS }) {
            return "ファイル名にパス区切り文字を含めることはできません: $fileName".left()
        }
        // "." / ".." はディレクトリ自身を指すため、ファイル名としては受け付けない
        if (fileName == "." || fileName == "..") {
            return "ファイル名として不正です: $fileName".left()
        }
        return fileName.right()
    }

    /**
     * ファイル名を指定ディレクトリ配下に閉じ込めて解決する
     *
     * [validate] を通していても、シンボリックリンクなどで配下から外れる余地が残る。
     * 実際に書き込む直前に正規化したパスで再確認する多重防御として使う。
     *
     * @param parentDirectory 配置先ディレクトリ
     * @param fileName 配置するファイル名
     * @return ディレクトリ配下に収まる File、外れる場合は理由
     */
    fun resolveInside(
        parentDirectory: File,
        fileName: String
    ): Either<String, File> {
        validate(fileName).onLeft { return it.left() }

        val parentPath = parentDirectory.toPath().toAbsolutePath().normalize()
        val resolvedPath = parentPath.resolve(fileName).normalize()

        // 直下にあること（親と一致する、あるいは配下から外れる場合を弾く）
        if (!resolvedPath.startsWith(parentPath) || resolvedPath == parentPath) {
            return "ファイルの配置先がディレクトリの外を指しています: $fileName".left()
        }
        return resolvedPath.toFile().right()
    }
}