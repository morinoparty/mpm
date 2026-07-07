/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
and related and neighboring rights to this software to the public domain worldwide.
This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.repository

/**
 * リポジトリソースマネージャーのインターフェース
 * 複数のリポジトリソースを優先順位順に管理する
 */
interface RepositoryManager {
    /**
     * 利用可能なすべてのプラグインの一覧を取得
     * 複数のソースから重複を除いて返す
     * @return プラグイン名のリスト
     */
    suspend fun getAvailablePlugins(): List<String>

    /**
     * 指定したプラグインのリポジトリファイルを取得
     * 優先順位順にソースを検索し、最初に見つかったものを返す
     * @param pluginName プラグイン名
     * @return リポジトリファイルの内容、見つからない場合はnull
     */
    suspend fun getRepositoryFile(pluginName: String): RepositoryFile?

    /**
     * 複数のplugin.ymlの`name`を、リポジトリ上の正規プラグイン名（リポジトリファイル名）へ一括解決する
     *
     * 照合は以下の順で行い、いずれも大文字小文字を無視する:
     * 1. リポジトリファイル名との一致（従来の挙動。安価）
     * 2. リポジトリファイルの`id`との一致
     * 3. リポジトリファイルの`aliases`のいずれかとの一致
     *
     * `mpm adopt` で、plugin.ymlの名前がリポジトリファイル名と異なる場合でも
     * 正しいリポジトリエントリに取り込めるようにするために使用する。
     * id/aliasの照合が必要な場合でも各リポジトリファイルの読み込みは1回に抑えるため、
     * 候補ごとに個別解決するよりリクエスト数が大幅に少ない。
     *
     * @param pluginYmlNames plugin.ymlの`name`のコレクション
     * @return 一致した名前のみを含む「入力名 → 正規プラグイン名」のマップ（未一致は含まない）
     */
    suspend fun resolvePluginNames(pluginYmlNames: Collection<String>): Map<String, String>

    /**
     * 利用可能なソースの一覧を取得
     * @return 利用可能なソースのリスト
     */
    suspend fun getAvailableSources(): List<PluginRepositorySource>

    /**
     * すべてのリポジトリソースを取得
     * @return リポジトリソースのリスト
     */
    fun getRepositorySources(): List<PluginRepositorySource>

    /**
     * 設定を再読み込みしてリポジトリソースを再構築する
     * デフォルト実装は何もしない（後方互換性のため）
     */
    fun reload() {}

    /**
     * 保持しているリポジトリソースのリソース（HTTPクライアント等）を解放する
     * プラグイン無効化時に呼び出すことでコネクションリークを防ぐ
     * デフォルト実装は何もしない（後方互換性のため）
     */
    fun shutdown() {}
}