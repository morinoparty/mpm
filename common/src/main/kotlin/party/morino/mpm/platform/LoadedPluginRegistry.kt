/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.platform

import java.io.File

/**
 * サーバーに読み込まれているプラグインのJARを問い合わせる
 *
 * 稼働中のプラグインが読み込んでいるJARを消すとクラスローダー経由のリソース読み込みが壊れるため、
 * JARの削除前にこの情報で「使用中かどうか」を判定する。
 */
interface LoadedPluginRegistry {
    /**
     * 読み込まれているすべてのプラグイン（無効化済みを含む）のJARを返す
     *
     * 無効化済みのプラグインもクラスローダーがJARを参照し続けるため含める。
     *
     * @return 各プラグインの読み込み元JAR（取得できないものは除く）
     */
    fun loadedJars(): List<File>

    /**
     * 現在有効なプラグインが読み込んでいるJARを返す
     *
     * 無効化済みのプラグインはJARを使い終えているため除く。
     * サーバー停止中に「まだ停止していないプラグインのJAR」を判定するために使う。
     *
     * @return 有効な各プラグインの読み込み元JAR（取得できないものは除く）
     */
    fun enabledJars(): List<File>
}