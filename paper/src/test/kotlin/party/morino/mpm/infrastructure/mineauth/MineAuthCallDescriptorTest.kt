/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.DataInputStream
import java.io.File

/**
 * MineAuth を呼び出すメソッドのディスクリプタに、シェードされた Kotlin の型が
 * 混入していないことをバイトコードレベルで検証する
 *
 * mpm は shadowJar で kotlin 標準ライブラリを自身の JAR に同梱しているのに対し、
 * MineAuth は Paper の `libraries:` 機能でランタイムに読み込んでいる。
 * そのため両者の `kotlin.*` クラスは別のクラスローダーが定義した別物であり、
 * クラスローダーをまたぐ呼び出しのディスクリプタに `kotlin/` の型が現れると
 * JVM の loader constraint violation（[LinkageError]）になる。
 *
 * 典型例は MineAuth のコンストラクタをデフォルト引数付きで呼ぶケースで、
 * Kotlin が合成する `(..., I, Lkotlin/jvm/internal/DefaultConstructorMarker;)V` の
 * ブリッジコンストラクタが選ばれてしまう。
 * この事故は 2つのプラグインクラスローダーが同居する実サーバーでしか再現しないため、
 * 単体テストでは再現できない。代わりにコンパイル済みクラスの定数プールを走査し、
 * 危険なディスクリプタが生成されていないことをビルド時に検出する。
 */
@DisplayName("MineAuth call descriptors carry no shaded Kotlin types")
class MineAuthCallDescriptorTest {
    @Test
    @DisplayName("no descriptor mixes a MineAuth type with a shaded kotlin type")
    fun descriptorsAreFreeOfShadedKotlinTypes() {
        val classFiles = mineauthPackageClassFiles()
        // 対象クラスが1件も見つからない場合は検証が素通りしてしまうため、明示的に失敗させる
        assertTrue(
            classFiles.isNotEmpty(),
            "no compiled class files found under $MINEAUTH_PACKAGE_PATH - the test cannot verify anything"
        )

        // ファイル名付きで違反を集める（複数あってもまとめて報告できるようにする）
        val violations =
            classFiles.flatMap { file ->
                utf8Constants(file)
                    .filter { it.isUnsafeCrossLoaderDescriptor() }
                    .map { "${file.name}: $it" }
            }

        assertTrue(
            violations.isEmpty(),
            "cross-classloader call descriptors must not reference shaded kotlin types " +
                "(construct MineAuth types with all arguments explicitly):\n" +
                violations.joinToString("\n")
        )
    }

    /**
     * 定数プールの UTF8 定数が「MineAuth の型とシェードされた Kotlin の型を同時に含むディスクリプタ」か判定する
     *
     * ジェネリクス情報を持つ Signature 属性は実際の呼び出しに使われないため、`<` を含む文字列は除外する。
     */
    private fun String.isUnsafeCrossLoaderDescriptor(): Boolean =
        startsWith("(") &&
            !contains("<") &&
            contains(MINEAUTH_TYPE_PREFIX) &&
            contains(KOTLIN_TYPE_PREFIX)

    /**
     * コンパイル済みクラスの出力ディレクトリから、MineAuth 連携パッケージの `.class` を列挙する
     *
     * テスト実行時のクラスパスから解決するため、ビルド構成のパスをハードコードせずに済む。
     */
    private fun mineauthPackageClassFiles(): List<File> {
        val codeSource =
            MpmPluginHandler::class.java.protectionDomain.codeSource.location
                .toURI()
        val packageDir = File(File(codeSource), MINEAUTH_PACKAGE_PATH)
        if (!packageDir.isDirectory) return emptyList()
        return packageDir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
    }

    /**
     * クラスファイルの定数プールを走査して、UTF8 定数（タグ1）をすべて取り出す
     *
     * ASM などの依存を増やさずに済むよう、定数プールのエントリ長だけを自前で読み飛ばす。
     */
    private fun utf8Constants(file: File): List<String> {
        DataInputStream(file.inputStream().buffered()).use { input ->
            input.readInt() // magic (0xCAFEBABE)
            input.readUnsignedShort() // minor version
            input.readUnsignedShort() // major version
            val constantPoolCount = input.readUnsignedShort()
            val constants = mutableListOf<String>()
            // 定数プールのインデックスは1始まりで、末尾は constantPoolCount - 1
            var index = 1
            while (index < constantPoolCount) {
                when (val tag = input.readUnsignedByte()) {
                    CONSTANT_UTF8 -> constants += input.readUTF()
                    // Long / Double は定数プールを2スロット消費する（JVMS 4.4.5）
                    CONSTANT_LONG, CONSTANT_DOUBLE -> {
                        input.skipNBytes(8)
                        index++
                    }
                    else -> input.skipNBytes(constantEntrySize(tag))
                }
                index++
            }
            return constants
        }
    }

    /**
     * UTF8 / Long / Double 以外の定数プールエントリの固定長を返す
     *
     * @throws IllegalStateException 未知のタグの場合（クラスファイル形式の想定外）
     */
    private fun constantEntrySize(tag: Int): Long =
        when (tag) {
            CONSTANT_CLASS, CONSTANT_STRING, CONSTANT_METHOD_TYPE, CONSTANT_MODULE, CONSTANT_PACKAGE -> 2L
            CONSTANT_METHOD_HANDLE -> 3L
            CONSTANT_INTEGER, CONSTANT_FLOAT, CONSTANT_FIELDREF, CONSTANT_METHODREF,
            CONSTANT_INTERFACE_METHODREF, CONSTANT_NAME_AND_TYPE, CONSTANT_DYNAMIC,
            CONSTANT_INVOKE_DYNAMIC -> 4L
            else -> error("unknown constant pool tag: $tag")
        }

    private companion object {
        // 走査対象のパッケージ（MineAuth を直接呼ぶコードはすべてこの配下にある）
        const val MINEAUTH_PACKAGE_PATH = "party/morino/mpm/infrastructure/mineauth"

        // ディスクリプタ中の MineAuth 型 / シェードされた Kotlin 型の目印
        const val MINEAUTH_TYPE_PREFIX = "party/morino/mineauth/"
        const val KOTLIN_TYPE_PREFIX = "Lkotlin/"

        // JVMS 4.4 の定数プールタグ
        const val CONSTANT_UTF8 = 1
        const val CONSTANT_INTEGER = 3
        const val CONSTANT_FLOAT = 4
        const val CONSTANT_LONG = 5
        const val CONSTANT_DOUBLE = 6
        const val CONSTANT_CLASS = 7
        const val CONSTANT_STRING = 8
        const val CONSTANT_FIELDREF = 9
        const val CONSTANT_METHODREF = 10
        const val CONSTANT_INTERFACE_METHODREF = 11
        const val CONSTANT_NAME_AND_TYPE = 12
        const val CONSTANT_METHOD_HANDLE = 15
        const val CONSTANT_METHOD_TYPE = 16
        const val CONSTANT_DYNAMIC = 17
        const val CONSTANT_INVOKE_DYNAMIC = 18
        const val CONSTANT_MODULE = 19
        const val CONSTANT_PACKAGE = 20
    }
}