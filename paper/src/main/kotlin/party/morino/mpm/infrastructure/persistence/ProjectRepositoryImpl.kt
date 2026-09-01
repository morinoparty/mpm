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

package party.morino.mpm.infrastructure.persistence

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.migration.SchemaVersions
import party.morino.mpm.api.domain.plugin.model.VersionSpecifierParser
import party.morino.mpm.api.domain.project.dto.MpmConfig
import party.morino.mpm.api.domain.project.model.MpmProject
import party.morino.mpm.api.domain.project.repository.ProjectRepository
import party.morino.mpm.api.shared.error.MpmError
import party.morino.mpm.infrastructure.migration.AtomicFileWriter
import party.morino.mpm.infrastructure.migration.SchemaVersionGuard
import party.morino.mpm.utils.Utils
import java.io.File
import java.io.IOException
import java.util.logging.Logger

/**
 * プロジェクトリポジトリの実装クラス
 *
 * mpm.jsonファイルの読み書きを担当する
 */
class ProjectRepositoryImpl :
    ProjectRepository,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()

    // 保存を中止した事実を利用者に伝えるためのロガー
    // JavaPlugin を注入するとこのクラスの利用側（テストを含む）に Bukkit 依存が波及するため、
    // 既存の AbstractPluginDownloader / ServerBackupManagerImpl と同じく標準の Logger を使う
    private val logger: Logger = Logger.getLogger(ProjectRepositoryImpl::class.java.name)

    /**
     * プロジェクトを取得
     *
     * mpm.jsonファイルを読み込んでMpmProjectを返す
     * 存在しない場合はnullを返す
     */
    override suspend fun find(): MpmProject? {
        val configFile = getConfigFile()
        if (!configFile.exists()) {
            return null
        }

        return try {
            val jsonString = configFile.readText()
            val config = Utils.json.decodeFromString<MpmConfig>(jsonString)
            MpmProject.fromDto(config) { versionString ->
                VersionSpecifierParser.parse(versionString)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * プロジェクトを取得（エラー情報付き）
     *
     * ファイル未存在とパースエラーを区別して返す
     */
    override suspend fun findOrError(): Either<MpmError, MpmProject> {
        val configFile = getConfigFile()
        if (!configFile.exists()) {
            return MpmError.ProjectError.ConfigNotFound.left()
        }

        return try {
            val jsonString = configFile.readText()
            val config = Utils.json.decodeFromString<MpmConfig>(jsonString)
            MpmProject
                .fromDto(config) { versionString ->
                    VersionSpecifierParser.parse(versionString)
                }.right()
        } catch (e: Exception) {
            MpmError.ProjectError.ConfigParseError(e.message ?: "Unknown error").left()
        }
    }

    /**
     * プロジェクトを保存
     *
     * MpmProjectをMpmConfigに変換してmpm.jsonに保存する。
     *
     * ディスク上の mpm.json が現行スキーマ版数より新しい場合は、書き込むと
     * ダウングレードになってしまうため保存を中止する。
     *
     * このとき警告ログを出すだけで正常終了すると、呼び出し側からは保存が成功した場合と
     * 区別が付かず、`mpm pin` が「固定しました」と表示したり、`mpm uninstall` が
     * mpm.json にプラグインを残したまま成功を返したりする。
     * [ProjectRepository.save] は Unit を返す契約でシグネチャを変えられないが、
     * 呼び出し側はいずれも既に `catch (e: Exception)` で保存失敗をユーザー向けエラーへ
     * 変換しているため、例外として送出すれば拒否理由をそのまま利用者へ届けられる。
     *
     * @throws IOException ディスク上のファイルが現行スキーマ版数より新しく保存を中止した場合、
     *   または原子的な置換ができず書き込みを中止した場合
     */
    override suspend fun save(project: MpmProject) {
        val configFile = getConfigFile()

        // 未来版数のファイルを巻き戻さないためのガード（書き込み前に必ず判定する）
        SchemaVersionGuard.ensureJsonWritable(configFile).onLeft { reason ->
            logger.warning("mpm.jsonの保存を中止しました: $reason")
            // 無音で成功扱いにすると、メモリ上の状態とディスクが乖離したまま
            // 呼び出し側が操作の成功を報告してしまうため、必ず失敗として伝播させる
            throw IOException(reason)
        }

        val sortedProject = project.withSortedPlugins()
        // 書き込み時は常に現行スキーマ版数をスタンプする
        // （MpmProject は schemaVersion を保持しないため、ここで付与しないと
        //   マイグレート済みの mpm.json が保存のたびにレガシー版数へ巻き戻ってしまう）
        val config = sortedProject.toDto().copy(schemaVersion = SchemaVersions.CURRENT)
        val jsonString = Utils.json.encodeToString(MpmConfig.serializer(), config)

        // 書き込み中のクラッシュでmpm.jsonが壊れないよう、一時ファイルに書き込んでから
        // 原子的な置換で本体へ反映する。置換できない場合はcopyで代替せず、そのまま失敗させる
        // （copyは原子的ではなく、途中で失敗するとmpm.jsonが切り詰められた状態で残るため。
        //   プロジェクト全体の宣言を失うより、保存を失敗させて元ファイルを残す方が安全）
        AtomicFileWriter.write(configFile, jsonString).onLeft { reason ->
            logger.warning("mpm.jsonの保存に失敗しました: $reason")
            throw IOException(reason)
        }
    }

    /**
     * mpm.json を保存してよいかを事前に判定する（副作用なし）
     *
     * 判定内容は [save] の書き込み前ガードと同一で、ディスク上のファイルを読むだけで
     * 何も書き換えない。JARの削除など後戻りできない操作の前に呼ぶことで、
     * 「保存は失敗したのにJARだけ消えた」という中途半端な状態を防ぐ。
     */
    override suspend fun ensureSavable(): Either<String, Unit> = SchemaVersionGuard.ensureJsonWritable(getConfigFile())

    /**
     * プロジェクトが存在するかどうかを確認
     */
    override suspend fun exists(): Boolean = getConfigFile().exists()

    /**
     * プロジェクトを削除
     *
     * mpm.jsonファイルを削除する
     */
    override suspend fun delete(): Boolean {
        val configFile = getConfigFile()
        return if (configFile.exists()) {
            configFile.delete()
        } else {
            false
        }
    }

    /**
     * mpm.jsonファイルのパスを取得する
     */
    private fun getConfigFile(): File {
        val rootDir = pluginDirectory.getRootDirectory()
        return File(rootDir, "mpm.json")
    }
}