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

package party.morino.mpm.api.domain.project.lock

import kotlinx.serialization.Serializable

/**
 * mpmのロックファイル（mpm-lock.yaml）
 *
 * npmの`package-lock.json`に相当し、実際にインストールされたプラグインの正確な
 * バージョンとダウンロード情報を記録する。`mpm install --frozen` はこのファイルの
 * 内容どおりにインストールすることで、環境をまたいだ再現性を保証する。
 *
 * @param lockfileVersion ロックファイルのフォーマットバージョン（互換性判定用）
 * @param generatedAt 生成日時（ISO 8601形式）
 * @param plugins プラグイン名 → ロックエントリのマップ
 */
@Serializable
data class MpmLock(
    val lockfileVersion: String,
    val generatedAt: String,
    val plugins: Map<String, LockEntry>
) {
    companion object {
        /** 現在のロックファイルフォーマットバージョン */
        const val CURRENT_LOCKFILE_VERSION: String = "1.0"
    }
}