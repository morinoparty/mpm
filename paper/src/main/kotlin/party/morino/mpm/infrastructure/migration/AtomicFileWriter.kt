/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.migration

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * 一時ファイル経由で「壊れないこと」を最優先にファイルを書き換えるユーティリティ
 *
 * ## copy へフォールバックしない理由
 * 一時ファイルへ書いてから rename する実装では、rename に失敗した際に
 * `copyTo(overwrite = true)` で代替したくなる。しかし copy は原子的ではなく、
 * （移動先を削除してから書き直すため）コピー中にプロセスが落ちたり容量が尽きたりすると、
 * 書き込み先が「部分的に上書きされた壊れたファイル」になってしまう。
 *
 * 「書き込めないこと」より「元ファイルを壊すこと」の方が遥かに深刻なため、
 * ここでは原子的な move ができない場合にフォールバックせず、そのまま失敗として返す。
 * 呼び出し側は、起動処理を止めたくない場面（マイグレーション、config.json の保存）では
 * そのファイルだけを失敗扱いにして続行し、操作の成否を利用者へ返すべき場面
 * （mpm.json の保存）では失敗として伝播させる。
 *
 * mpmが書き込む設定ファイル（メタデータYAML・mpm.json・config.json・移行対象）は
 * いずれもこの経路を通す。
 */
object AtomicFileWriter {
    /** 一時ファイルに付与する拡張子 */
    private const val TEMP_SUFFIX = ".tmp"

    /**
     * 一時ファイルに書き出してから原子的な move で [target] に反映する
     *
     * [File.renameTo] ではなく [Files.move] + [StandardCopyOption.ATOMIC_MOVE] を使う。
     * renameTo は Windows で「移動先が既に存在する」だけで失敗するのに対し、
     * ATOMIC_MOVE は移動先の置換を含めて原子的に行えるため、失敗する場面がむしろ少ない。
     *
     * move に失敗した場合、[target] には一切手を触れずに失敗を返す（元ファイルは無傷のまま）。
     * 一時ファイルは成功・失敗のいずれでも必ず後始末する。
     *
     * 一時ファイル名は [File.createTempFile] で毎回一意にする。固定名にすると、同じファイルを
     * 別スレッドから同時に書いた場合（cronの自動更新と利用者のコマンドなど）に、2つの書き込みが
     * 同じ一時ファイルを取り合って混線した内容を作り、それを ATOMIC_MOVE が「原子的に」
     * 本体へ設置してしまう。クラッシュが一度も起きなくても本体が壊れるため、
     * rename の原子性だけでは内容の一貫性を保証できない。
     *
     * なお一意化で防げるのは「壊れたファイルの設置」までで、
     * 同時書き込みの後勝ち（先の変更が失われること）は残る。
     *
     * @param target 書き込み先ファイル
     * @param content 書き込む内容
     * @return 成功した場合はUnit、失敗した場合は理由
     */
    fun write(
        target: File,
        content: String
    ): Either<String, Unit> {
        // finally で後始末するため、try の外で一時ファイルを確定させておく
        val tempFile =
            try {
                File.createTempFile(target.name, TEMP_SUFFIX, target.parentFile)
            } catch (e: Exception) {
                return "${target.name} を安全に書き換えられませんでした（一時ファイルを作成できません）: ${e.message}".left()
            }
        return try {
            tempFile.writeText(content)
            Files.move(
                tempFile.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE
            )
            Unit.right()
        } catch (e: Exception) {
            // 原子的に置き換えられない環境・状況では、あえて何もせず失敗させる
            // （copy+delete で代替すると元ファイルを壊す可能性があるため）
            "${target.name} を安全に書き換えられませんでした（原子的な置換に失敗）: ${e.message}".left()
        } finally {
            // move が成功していれば一時ファイルは既に存在しないため、delete() は何もしない
            tempFile.delete()
        }
    }
}