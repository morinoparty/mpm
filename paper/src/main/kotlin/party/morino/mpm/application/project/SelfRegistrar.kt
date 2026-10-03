/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.application.project

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import org.bukkit.plugin.java.JavaPlugin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.application.plugin.IntegrityVerifier
import party.morino.mpm.api.domain.downloader.DownloaderRepository
import party.morino.mpm.api.domain.plugin.model.PluginName
import party.morino.mpm.api.domain.plugin.model.PluginSpec
import party.morino.mpm.api.domain.plugin.model.VersionSpecifier
import party.morino.mpm.api.domain.plugin.service.PluginMetadataManager
import party.morino.mpm.api.domain.project.model.MpmProject
import party.morino.mpm.api.domain.repository.RepositoryManager
import party.morino.mpm.application.plugin.JarVersionMatcher
import party.morino.mpm.application.plugin.toUrlData
import party.morino.mpm.utils.runningJarOf

/**
 * `mpm init` の時点で mpm 自身を管理対象（latest）として登録する
 *
 * mpm 自身も他のプラグインと同じく `mpm update` やスケジュール自動更新で更新できるため、
 * 初期化の段階から mpm.json に `"mpm": "latest"` として載せておく。
 *
 * 他のプラグインの adopt と違い、ここではダウンロードを一切行わない。
 * 実行中のJARを差し替えるとクラスローダー経由の読み込みが壊れるため、
 * 実行中のJARのバージョンをリポジトリ上のバージョンへ照合し、そのJARを指すメタデータだけを作る。
 * 以後の更新は通常の更新経路（旧JARはサーバー停止時に削除）に任せる。
 */
class SelfRegistrar : KoinComponent {
    // KoinによるDI
    private val plugin: JavaPlugin by inject()
    private val repositoryManager: RepositoryManager by inject()
    private val downloaderRepository: DownloaderRepository by inject()
    private val metadataManager: PluginMetadataManager by inject()
    private val integrityVerifier: IntegrityVerifier by inject()

    /**
     * mpm 自身のメタデータを作成し、プロジェクトに管理対象として追加する
     *
     * メタデータは保存するが、mpm.json の保存は呼び出し側が行う。
     *
     * @param project mpm 自身を追加する前のプロジェクト（mpm 自身のエントリを含まないこと）
     * @param versionRequirement mpm.json に書くバージョン指定（init では latest）
     * @return mpm 自身を追加したプロジェクト、登録できない場合はその理由
     */
    suspend fun register(
        project: MpmProject,
        versionRequirement: VersionSpecifier = VersionSpecifier.Latest
    ): Either<String, MpmProject> {
        // リポジトリ上の名前と plugin.yml の名前は一致している前提（中央リポジトリの mpm.json）
        val pluginName = plugin.name

        // 実行中のJARを特定する（開発環境などJARから読み込まれていない場合は登録しない）
        val runningJar =
            runningJarOf(plugin)?.takeIf { it.isFile && it.extension == "jar" }
                ?: return "実行中のJARを特定できません".left()

        // リポジトリから mpm 自身の定義を取得する
        val repositoryFile =
            repositoryManager.getRepositoryFile(pluginName)
                ?: return "リポジトリに $pluginName の定義が見つかりません".left()
        val firstRepository =
            repositoryFile.repositories.firstOrNull()
                ?: return "$pluginName のリポジトリ設定が空です".left()
        val urlData =
            firstRepository.toUrlData()
                ?: return "未対応のリポジトリタイプです: ${firstRepository.type}".left()

        // 実行中のバージョンをリポジトリ上のバージョンへ照合する（downloadIdを得るため）
        val runningVersion = plugin.pluginMeta.version
        val allVersions =
            try {
                downloaderRepository.getAllVersions(urlData)
            } catch (e: Exception) {
                return "リポジトリへの接続に失敗しました: ${e.message}".left()
            }
        val versionData =
            JarVersionMatcher.findMatchingVersion(runningVersion, allVersions, firstRepository.versionPattern)
                ?: return "バージョン '$runningVersion' がリポジトリに見つかりません".left()

        // 未来のスキーマ版数で書かれたメタデータを巻き戻さないよう、書き込み前に確認する
        metadataManager.ensureMetadataReplaceable(pluginName).onLeft { return it.left() }

        // メタデータの作成。ダウンロードしないため、ファイル名とハッシュは実行中のJARから埋める
        val created =
            metadataManager
                .createMetadata(pluginName, firstRepository, versionData, "init", "latest")
                .getOrElse { return it.left() }
        val sha256 = integrityVerifier.computeSha256(runningJar)

        metadataManager
            .withMetadataLock(pluginName) {
                // init --overwrite で作り直す場合も、lock などの設定は引き継ぐ
                val previousSettings =
                    metadataManager
                        .loadMetadata(pluginName)
                        .getOrNull()
                        ?.mpmInfo
                        ?.settings
                val metadata =
                    created.copy(
                        mpmInfo =
                            created.mpmInfo.copy(
                                download = created.mpmInfo.download.copy(fileName = runningJar.name, sha256 = sha256),
                                settings = previousSettings ?: created.mpmInfo.settings
                            )
                    )
                metadataManager.saveMetadata(pluginName, metadata)
            }.onLeft { return it.left() }

        // mpm.json 上は指定されたバージョン指定（既定は latest）で管理する
        return project
            .addPlugin(PluginSpec.Managed(PluginName(pluginName), versionRequirement))
            .mapLeft { it.message }
    }
}