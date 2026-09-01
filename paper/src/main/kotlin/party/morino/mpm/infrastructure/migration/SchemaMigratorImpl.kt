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
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.migration.SchemaMigrationOutcome
import party.morino.mpm.api.domain.migration.SchemaMigrationReport
import party.morino.mpm.api.domain.migration.SchemaMigrator
import party.morino.mpm.api.domain.migration.SchemaVersions
import party.morino.mpm.api.domain.plugin.dto.ManagedPluginDto
import party.morino.mpm.utils.Utils
import java.io.File

/**
 * 設定ファイルのスキーママイグレーションを行う実装クラス
 *
 * ## 設計の要: probe（版数の先読み）を必ず先に行う
 * ファイルはまず「生パース」で schemaVersion だけを読み取り、
 * 現行版数より古い場合に限って実際の変換を行う。この順序は必須であり、崩してはならない。
 *
 * - `Utils.json` は `ignoreUnknownKeys = true` のため、未来の版数で増えたキーを
 *   型付きデコード → エンコードすると黙って消してしまう（データ破壊）
 * - `Yaml.default` は `strictMode = true` のため、未来の版数のファイルを
 *   型付きデコードすると例外になる
 *
 * probe を先に行うことで、この2つの問題を同時に回避している。
 *
 * ログ出力は行わず、結果を [SchemaMigrationReport] として返すだけに留める。
 * 呼び出し側（Mpm.onEnable）がログの文言を決めることで、Bukkit 非依存に保っている。
 */
class SchemaMigratorImpl :
    SchemaMigrator,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()

    // probe 専用の非strictな Yaml。未知フィールドを無視して schemaVersion だけを読むために使う。
    // Yaml.default は strictMode = true なので、未来の版数で増えたフィールドを持つファイルで例外になってしまう
    private val probeYaml = Yaml(configuration = YamlConfiguration(strictMode = false))

    override suspend fun migrateAll(): SchemaMigrationReport =
        // throw しない契約を構造的に保証する（起動処理を絶対に止めないため）
        runCatching { migrateAllOrThrow() }
            .getOrElse { cause ->
                val reason = cause.message ?: cause::class.simpleName ?: "不明なエラー"
                SchemaMigrationReport(listOf(SchemaMigrationOutcome.Failed(ALL_FILES_LABEL, reason)))
            }

    /**
     * 対象ファイルを走査してマイグレートする本体
     *
     * @return マイグレーション結果のレポート
     */
    private fun migrateAllOrThrow(): SchemaMigrationReport {
        val rootDir = pluginDirectory.getRootDirectory()
        val outcomes = mutableListOf<SchemaMigrationOutcome>()

        // 1ファイルの失敗が他ファイルの処理を止めないよう、対象ごとに独立して実行する
        outcomes +=
            guarded(MPM_JSON_FILE_NAME) {
                migrateJsonFile(File(rootDir, MPM_JSON_FILE_NAME), MPM_JSON_STEPS)
            }
        outcomes +=
            guarded(CONFIG_JSON_FILE_NAME) {
                migrateJsonFile(File(rootDir, CONFIG_JSON_FILE_NAME), CONFIG_JSON_STEPS)
            }
        outcomes += migrateMetadataFiles()

        return SchemaMigrationReport(outcomes)
    }

    // ===== 対象ファイルごとの処理 =====

    /**
     * metadata ディレクトリ配下の yaml をすべてマイグレートする
     *
     * ディレクトリが存在しない・空の場合は結果を積まない（何も起きなかったものとして扱う）
     *
     * @return ファイルごとの結果
     */
    private fun migrateMetadataFiles(): List<SchemaMigrationOutcome> {
        // PluginDirectory の実装はディレクトリを副作用で作成するため、
        // 「ディレクトリが在る」ことをファイルが在ることの根拠にしない
        val metadataDir =
            try {
                pluginDirectory.getMetadataDirectory()
            } catch (e: Exception) {
                return listOf(SchemaMigrationOutcome.Failed(METADATA_DIR_NAME, "メタデータディレクトリを取得できませんでした: ${e.message}"))
            }

        // .tmp の残骸やサブディレクトリを拾わないよう、拡張子が yaml のファイルだけを対象にする
        val yamlFiles =
            metadataDir
                .listFiles()
                ?.filter { it.isFile && it.extension.equals("yaml", ignoreCase = true) }
                ?.sortedBy { it.name }
                ?: return emptyList()

        return yamlFiles.map { file ->
            val displayName = "$METADATA_DIR_NAME/${file.name}"
            guarded(displayName) { migrateMetadataFile(file, displayName) }
        }
    }

    /**
     * JSON ファイル1本をマイグレートする
     *
     * 型付きデコードを経由せず [JsonObject] の木のまま変換するため、
     * mpm が知らない拡張キーもそのまま保存される。
     *
     * @param file 対象ファイル
     * @param steps 適用するマイグレーションチェーン
     * @return マイグレーション結果
     */
    private fun migrateJsonFile(
        file: File,
        steps: List<SchemaMigrationStep<JsonObject>>
    ): SchemaMigrationOutcome {
        if (!file.exists()) return SchemaMigrationOutcome.Absent(file.name)

        val root =
            parseJsonObject(file.readText()).fold(
                { return SchemaMigrationOutcome.Failed(file.name, it) },
                { it }
            )

        // probe: 生パースの木から版数だけを読み取る
        // 値が数値以外・オブジェクトなどの場合は例外にせずレガシー扱いにフォールバックする
        val found = (root[SchemaVersions.FIELD_NAME] as? JsonPrimitive)?.intOrNull ?: SchemaVersions.LEGACY
        judgeVersion(file.name, found)?.let { return it }

        // ここに到達するのは found < CURRENT のときだけ
        val migrated =
            applyChain(root, found, steps).fold(
                { return SchemaMigrationOutcome.Failed(file.name, it) },
                { it }
            )
        // 変換後は必ず現行版数をスタンプする
        val stamped = JsonObject(migrated + (SchemaVersions.FIELD_NAME to JsonPrimitive(SchemaVersions.CURRENT)))

        return writeAtomically(file, Utils.json.encodeToString(JsonObject.serializer(), stamped)).fold(
            { SchemaMigrationOutcome.Failed(file.name, it) },
            { SchemaMigrationOutcome.Migrated(file.name, found, SchemaVersions.CURRENT) }
        )
    }

    /**
     * metadata の yaml ファイル1本をマイグレートする
     *
     * probe は非strictな Yaml で行うが、実際の変換は strict な [Yaml.default] で行う。
     * strict にしておけば、想定外のキーを持つファイルは変換されず [SchemaMigrationOutcome.Failed]
     * となり、キーが黙って消えることを防げる（安全側に倒す）。
     *
     * @param file 対象ファイル
     * @param displayName ログ表示用のファイル名
     * @return マイグレーション結果
     */
    private fun migrateMetadataFile(
        file: File,
        displayName: String
    ): SchemaMigrationOutcome {
        if (!file.exists()) return SchemaMigrationOutcome.Absent(displayName)

        val yamlString = file.readText()

        // probe: 未知フィールドを無視して版数だけを読み取る
        val found =
            try {
                probeYaml.decodeFromString(SchemaVersionProbe.serializer(), yamlString).schemaVersion
            } catch (e: Exception) {
                return SchemaMigrationOutcome.Failed(displayName, "YAML として読み込めませんでした: ${e.message}")
            }
        judgeVersion(displayName, found)?.let { return it }

        val dto =
            try {
                Yaml.default.decodeFromString(ManagedPluginDto.serializer(), yamlString)
            } catch (e: Exception) {
                return SchemaMigrationOutcome.Failed(displayName, "メタデータの解釈に失敗しました: ${e.message}")
            }

        val migrated =
            applyChain(dto, found, METADATA_STEPS).fold(
                { return SchemaMigrationOutcome.Failed(displayName, it) },
                { it }
            )
        // 変換後は必ず現行版数をスタンプする
        val stamped = migrated.copy(schemaVersion = SchemaVersions.CURRENT)

        return writeAtomically(file, Yaml.default.encodeToString(ManagedPluginDto.serializer(), stamped)).fold(
            { SchemaMigrationOutcome.Failed(displayName, it) },
            { SchemaMigrationOutcome.Migrated(displayName, found, SchemaVersions.CURRENT) }
        )
    }

    // ===== 共通ロジック =====

    /**
     * probe した版数から、変換が必要かどうかを判定する
     *
     * @param fileName ログ表示用のファイル名
     * @param found ファイルに記載されていた版数
     * @return 変換が不要な場合はその理由を表す結果、変換が必要な場合はnull
     */
    private fun judgeVersion(
        fileName: String,
        found: Int
    ): SchemaMigrationOutcome? =
        when {
            // 冪等性の要。現行版数なら読み書きを一切行わない
            found == SchemaVersions.CURRENT -> SchemaMigrationOutcome.AlreadyCurrent(fileName)
            // ダウングレードはできないため、データを壊さないことを最優先して触らない
            found > SchemaVersions.CURRENT -> SchemaMigrationOutcome.FutureVersion(fileName, found)
            else -> null
        }

    /**
     * probe した版数から現行版数まで、チェーンを1段階ずつ順に適用する
     *
     * ステップが欠けている（例: v2 -> v3 のステップが未定義）場合は変換不能として失敗にする
     *
     * @param initial 変換前の中間表現
     * @param fromVersion 変換前の版数
     * @param steps 適用するマイグレーションチェーン
     * @return 変換後の中間表現、または失敗理由
     */
    private fun <T> applyChain(
        initial: T,
        fromVersion: Int,
        steps: List<SchemaMigrationStep<T>>
    ): Either<String, T> {
        var current = initial
        var version = fromVersion
        while (version < SchemaVersions.CURRENT) {
            val step =
                steps.firstOrNull { it.from == version }
                    ?: return "スキーマ v$version から v${version + 1} への変換ステップが定義されていません".left()
            current = step.transform(current)
            version = step.to
        }
        return current.right()
    }

    /**
     * 想定外の例外がマイグレーション全体を巻き込まないよう、1ファイル分の処理を包む
     *
     * @param fileName ログ表示用のファイル名
     * @param block 1ファイル分の処理
     * @return 処理結果、または例外を包んだ失敗結果
     */
    private fun guarded(
        fileName: String,
        block: () -> SchemaMigrationOutcome
    ): SchemaMigrationOutcome =
        try {
            block()
        } catch (e: Exception) {
            SchemaMigrationOutcome.Failed(fileName, e.message ?: e::class.simpleName ?: "不明なエラー")
        }

    /**
     * JSON 文字列をオブジェクトとしてパースする
     *
     * 失敗理由（構文エラーの位置など）は移行失敗の切り分けに必要なため、
     * 例外を握り潰さずメッセージとして返す
     *
     * @param text JSON 文字列
     * @return パースしたオブジェクト、パースできない場合は理由
     */
    private fun parseJsonObject(text: String): Either<String, JsonObject> =
        try {
            (Utils.json.parseToJsonElement(text) as? JsonObject)?.right()
                ?: "JSON オブジェクトとして読み込めませんでした（オブジェクト以外の値です）".left()
        } catch (e: Exception) {
            "JSON として解釈できませんでした: ${e.message}".left()
        }

    /**
     * 一時ファイルに書いてから原子的な move で反映する（[AtomicFileWriter] への委譲）
     *
     * mpm が書き込む設定ファイル（mpm.json / config.json / metadata の yaml / 移行対象）は
     * すべて同じ [AtomicFileWriter] を通しており、原子的に置き換えられない場合は
     * copy で代替せず失敗させる（理由は [AtomicFileWriter] のKDocを参照）。
     *
     * 呼び出し側の方針だけがここ固有である。マイグレーションでは「移行できないこと」より
     * 「元ファイルを壊すこと」の方が深刻なため、失敗したファイルだけを
     * [SchemaMigrationOutcome.Failed] にして起動処理は継続させる。
     *
     * @param target 書き込み先ファイル
     * @param content 書き込む内容
     * @return 成功した場合はUnit、失敗した場合は理由
     */
    private fun writeAtomically(
        target: File,
        content: String
    ): Either<String, Unit> = AtomicFileWriter.write(target, content)

    companion object {
        /** プロジェクト定義ファイル名 */
        private const val MPM_JSON_FILE_NAME = "mpm.json"

        /** 設定ファイル名 */
        private const val CONFIG_JSON_FILE_NAME = "config.json"

        /** メタデータディレクトリ名（ログ表示用） */
        private const val METADATA_DIR_NAME = "metadata"

        /** 走査自体に失敗した場合のログ表示用ラベル */
        private const val ALL_FILES_LABEL = "設定ファイル全体"

        /**
         * config.json v1 -> v2: 廃止された settings.schedule.dryRun を除去する
         *
         * cron 自動更新の対象がバージョン指定によって決まるようになり dryRun が不要になったため、
         * ファイル上からも消して混乱を防ぐ
         *
         * @param root 変換前の JSON オブジェクト
         * @return dryRun を除去した JSON オブジェクト
         */
        private fun removeScheduleDryRun(root: JsonObject): JsonObject {
            val settings = root["settings"] as? JsonObject ?: return root
            val schedule = settings["schedule"] as? JsonObject ?: return root
            val newSchedule = JsonObject(schedule - "dryRun")
            val newSettings = JsonObject(settings + ("schedule" to newSchedule))
            return JsonObject(root + ("settings" to newSettings))
        }

        /** mpm.json のマイグレーションチェーン（v1 -> v2 は schemaVersion のスタンプのみ） */
        private val MPM_JSON_STEPS: List<SchemaMigrationStep<JsonObject>> =
            listOf(SchemaMigrationStep<JsonObject>(from = 1, to = 2) { it })

        /** config.json のマイグレーションチェーン（v1 -> v2 で settings.schedule.dryRun を除去する） */
        private val CONFIG_JSON_STEPS: List<SchemaMigrationStep<JsonObject>> =
            listOf(SchemaMigrationStep(from = 1, to = 2, transform = ::removeScheduleDryRun))

        /** metadata 配下の yaml のマイグレーションチェーン（v1 -> v2 は schemaVersion のスタンプのみ） */
        private val METADATA_STEPS: List<SchemaMigrationStep<ManagedPluginDto>> =
            listOf(SchemaMigrationStep<ManagedPluginDto>(from = 1, to = 2) { it })
    }
}