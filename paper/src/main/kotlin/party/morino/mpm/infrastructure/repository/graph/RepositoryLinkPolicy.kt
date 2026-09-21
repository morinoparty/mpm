/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.repository.graph

import party.morino.mpm.api.domain.repository.RepositoryConfig
import party.morino.mpm.api.domain.repository.RepositoryFile
import party.morino.mpm.api.domain.repository.model.RepositoryLink
import java.net.URI

/**
 * 子リポジトリへのリンク（[RepositoryLink]）に基づく信頼境界の判定をまとめた純粋関数群
 *
 * 外部依存を持たないため、[RepositoryGraphResolver] から切り出して単体でテストできる。
 * ここを通らない子リポジトリの定義は一切採用されない。
 */
object RepositoryLinkPolicy {
    // プラグイン名として許可する文字パターン（URLパスセグメントとして安全な文字のみ）
    private val PLUGIN_NAME_PATTERN = Regex("^[A-Za-z0-9_-]+$")

    // 子が名乗れるリポジトリタイプ。Downloaderが実装している種別に限定する
    private val ALLOWED_TYPES = setOf("modrinth", "github", "spigotmc", "hangar")

    // IPv4リテラルの判定用
    private val IPV4_PATTERN = Regex("^\\d+\\.\\d+\\.\\d+\\.\\d+$")

    /**
     * プラグイン名がリポジトリキーとして妥当かを返す
     * @param name プラグイン名
     * @return 英数字・ハイフン・アンダースコアのみで構成されていればtrue
     */
    fun isValidPluginName(name: String): Boolean = PLUGIN_NAME_PATTERN.matches(name)

    /**
     * 単一パターンとの一致判定。末尾 `*` のみをプレフィックス一致のワイルドカードとして扱う
     * @param value 判定対象
     * @param pattern パターン（完全一致、または `prefix*`）
     */
    private fun matchesPattern(
        value: String,
        pattern: String
    ): Boolean =
        if (pattern.endsWith("*")) {
            value.startsWith(pattern.dropLast(1))
        } else {
            value == pattern
        }

    /**
     * リポジトリ設定1件がリンクの allowedSources に収まるかを返す
     *
     * allowedSources が空のリンクは配布元を制限しない。
     * GitHub の `owner/repo` は大小文字を区別しないため、比較は小文字化して行う。
     * @param repo 判定対象のリポジトリ設定
     * @param link 判定に使うリンク
     * @return 制限が無い、または `type:id` が allowedSources のいずれかに一致すればtrue
     */
    fun matchesAllowedSource(
        repo: RepositoryConfig,
        link: RepositoryLink
    ): Boolean {
        if (link.allowedSources.isEmpty()) return true
        val actual = "${repo.type}:${repo.repositoryId}".lowercase()
        return link.allowedSources.any { matchesPattern(actual, it.lowercase()) }
    }

    /**
     * 子リポジトリ由来のプラグイン定義を採用してよいかを返す
     *
     * 名前に制限は無い（同名は祖先側が勝つ）。配布元は Downloader が実装している種別に限り、
     * 経路上のリンクに allowedSources があればそのすべてを満たす必要がある。
     *
     * @param name インデックス上のキー
     * @param file プラグイン定義
     * @param constraints ルートからこの定義に到達するまでに通ったリンク
     * @return 採用してよければtrue
     */
    fun isPermitted(
        name: String,
        file: RepositoryFile,
        constraints: List<RepositoryLink>
    ): Boolean {
        // キーと定義中のidが食い違うものは受け付けない（なりすまし防止）
        if (name != file.id) return false
        if (!isValidPluginName(name)) return false
        // 配布元が1件も無い定義は意味を持たない
        if (file.repositories.isEmpty()) return false
        // 未知のリポジトリタイプは Downloader が扱えないので弾く
        if (file.repositories.any { it.type !in ALLOWED_TYPES }) return false
        return constraints.all { link ->
            file.repositories.all { repo -> matchesAllowedSource(repo, link) }
        }
    }

    /**
     * 子リポジトリから受け取った定義のうち、子には書かせないフィールドを落とす
     *
     * - downloadUrl: 将来実装された場合に第三者が任意のURLを差し込める入口になる
     * - fileNameTemplate: FileNameTemplate.render がパス区切りをサニタイズしないため、
     *   `../` を含むテンプレートで plugins/ の外へ書き込める
     *
     * どちらも管理者が直接管理するルート（設定で指定したリポジトリ）に限って許可する。
     * @param file 子由来の定義
     * @return 危険なフィールドを除いた定義
     */
    fun stripUntrustedFields(file: RepositoryFile): RepositoryFile =
        file.copy(
            repositories =
                file.repositories.map { repo ->
                    repo.copy(downloadUrlTemplate = null, fileNameTemplate = null)
                }
        )

    /**
     * 子リポジトリへのリンク先URLとして安全かを返す
     *
     * 第三者のインデックスに書かれたURLをサーバーが取得しに行くため、
     * サーバー内部（メタデータエンドポイント等）を指せないよう
     * https 限定・IPリテラル/localhost/.local 拒否とする。
     * @param url リンク先URL
     * @return 安全であればtrue
     */
    fun isSafeChildUrl(url: String): Boolean {
        val uri =
            try {
                URI(url)
            } catch (e: Exception) {
                return false
            }
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        if (host == "localhost" || host.endsWith(".local")) return false
        // IPv4 / IPv6 リテラルを拒否する（IPv6 は URI.host が "[...]" 形式になる）
        if (IPV4_PATTERN.matches(host)) return false
        if (host.startsWith("[") || host.contains(":")) return false
        return true
    }
}