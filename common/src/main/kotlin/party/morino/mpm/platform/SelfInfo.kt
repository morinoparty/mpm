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
 * 実行中の mpm 自身に関する情報
 *
 * 自己更新の判定（実行中のJARを消さない）や、mpm 自身を管理対象に登録する処理で使う。
 * プラットフォームごとにプラグイン記述子の取得方法が異なるため、各プラットフォームのモジュールが実装する。
 */
interface SelfInfo {
    /**
     * mpm 自身のプラグイン名（Velocity の場合はプラグインID）
     */
    val id: String

    /**
     * 実行中の mpm のバージョン
     */
    val version: String

    /**
     * mpm のクラスを読み込んでいるJAR（取得できない場合は null）
     */
    val jar: File?
}