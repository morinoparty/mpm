/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.utils

import kotlinx.serialization.json.Json
import party.morino.mpm.api.domain.config.model.ConfigData

/**
 * テスト用の[ConfigData]ローダー
 *
 * Koinに依存せず、テストクラスパス上の `plugins/mpm/config.json` を直接読み込む。
 * 開発者はプロジェクトにコミットされないローカル上書きファイル
 * `plugins/mpm/config.local.json` を置くことで、GitHub tokenなどの
 * 機密値をローカル環境にのみ反映できる。
 *
 * 解決順序:
 * 1. `plugins/mpm/config.local.json` (存在すれば) — 機密値のローカル上書き
 * 2. `plugins/mpm/config.json` — コミットされたテンプレート
 */
object TestConfigLoader {
    // 未知フィールドを無視して将来拡張に耐える設定
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * テスト用の[ConfigData]を読み込む
     *
     * @return resources から読み込んだ[ConfigData]
     * @throws IllegalStateException いずれのテスト設定ファイルも見つからない場合
     */
    fun load(): ConfigData {
        val classLoader = this::class.java.classLoader
        val resource = classLoader.getResource("plugins/mpm/config.local.json")
            ?: classLoader.getResource("plugins/mpm/config.json")
            ?: throw IllegalStateException(
                "test config.json がクラスパス上に見つかりません: " +
                    "paper/src/test/resources/plugins/mpm/config.json を確認してください"
            )
        val text = resource.openStream().bufferedReader().use { it.readText() }
        return json.decodeFromString<ConfigData>(text)
    }
}
