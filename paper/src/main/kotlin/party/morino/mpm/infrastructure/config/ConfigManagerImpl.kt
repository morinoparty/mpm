/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.config

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.config.model.ConfigData
import party.morino.mpm.api.domain.migration.SchemaVersions
import party.morino.mpm.api.domain.config.ConfigManager
import party.morino.mpm.infrastructure.migration.AtomicFileWriter
import party.morino.mpm.infrastructure.migration.SchemaVersionGuard
import party.morino.mpm.utils.Utils
import java.io.File
import java.util.logging.Logger

/**
 * ConfigManagerの実装
 *
 * config.jsonの読み込み・管理を行う
 * 依存性はKoinによって注入される
 */
class ConfigManagerImpl :
    ConfigManager,
    ConfigLoadDiagnostics,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()

    // 読み込み失敗や保存中止を利用者に伝えるためのロガー
    // config.json の読み込みは onEnable の最初期に走るため、JavaPlugin を注入すると
    // 初期化順序とテストの両方で Bukkit 依存が邪魔になる。
    // 既存の ProjectRepositoryImpl / AbstractPluginDownloader と同じく標準の Logger を使う
    private val logger: Logger = Logger.getLogger(ConfigManagerImpl::class.java.name)

    // Volatileでスレッド間の可視性を保証（reload時の変更が即座に他スレッドから見える）
    @Volatile
    private lateinit var configData: ConfigData

    // 直近の読み込みでデフォルトへフォールバックした理由。フォールバックしていなければnull
    // （configDataと同じ理由でVolatile。`/mpm reload` は別スレッドから読む）
    @Volatile
    override var lastLoadFailure: String? = null
        private set

    /**
     * 現在の設定を取得する
     *
     * キャッシュがある場合はそれを返し、ない場合はファイルから読み込む
     *
     * @return 現在のConfigData
     */
    override fun getConfig(): ConfigData {
        return configData
    }

    /**
     * config.jsonを再読み込みする
     *
     * ファイルから設定を読み込み直し、キャッシュを更新する
     */
    override suspend fun reload() {
        configData = loadConfigFromFile()
    }

    /**
     * config.jsonをファイルから読み込む
     *
     * ファイルが存在しない場合はデフォルト値を使用し、ファイルを作成する
     *
     * @return 読み込んだConfigData
     */
    private fun loadConfigFromFile(): ConfigData {
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "config.json")

        // ファイルが存在しない場合はデフォルト値を使用
        if (!configFile.exists()) {
            // ファイルが無いのは初回起動の通常経路であり、設定を失ったわけではない
            lastLoadFailure = null
            val defaultConfig = ConfigData()
            // 書き出せない環境（権限不足・容量不足など）でも起動は続行する。
            // 設定の作成に失敗しただけでサーバーの起動を止める理由はない
            runCatching { saveConfigToFile(defaultConfig) }.onFailure { e ->
                logger.warning(
                    "config.jsonを作成できませんでした。デフォルト設定で起動します" +
                        "（今回の起動では設定は保存されません）: ${configFile.absolutePath} - ${e.message}"
                )
            }
            return defaultConfig
        }

        // 読み込み（readText）自体も失敗しうる点に注意。
        // 読み取り権限が無い場合や、config.json が同名のディレクトリだった場合は例外になり、
        // tryの外に置くと onEnable ごと落ちてしまう。マイグレータが「1ファイルの失敗で
        // 起動全体を止めない」方針である以上、ここも同じ方針に揃える必要がある。
        return try {
            val jsonString = configFile.readText()
            val loaded = Utils.json.decodeFromString<ConfigData>(jsonString)
            lastLoadFailure = null
            loaded
        } catch (e: Exception) {
            // config.jsonが壊れている/読み取れない場合はプラグイン有効化を中断させず、
            // デフォルト値にフォールバックして警告を出す。
            // 「設定が無音で失われた」と誤解されないよう、対象パスと、
            // ファイルには手を触れていないことを明示する
            logger.warning(
                "config.jsonの読み込みに失敗したため、デフォルト設定で起動します" +
                    "（ファイルは変更していません。内容を確認して修正してください）: " +
                    "${configFile.absolutePath} - ${e.message}"
            )
            // コンソールを見ていない利用者にも伝わるよう、`/mpm reload` から参照できる形で残す
            lastLoadFailure = "${configFile.absolutePath} - ${e.message}"
            ConfigData()
        }
    }

    /**
     * ConfigDataをconfig.jsonに保存する
     *
     * ディスク上の config.json が現行スキーマ版数より新しい場合は、書き込むと
     * ダウングレードになってしまうため保存を中止して警告ログのみを出す。
     * 設定の保存に失敗してもプラグインの起動を止めるべきではないため、例外にはしない。
     *
     * @param config 保存するConfigData
     */
    private fun saveConfigToFile(config: ConfigData) {
        val rootDir = pluginDirectory.getRootDirectory()
        val configFile = File(rootDir, "config.json")

        // 未来版数のファイルを巻き戻さないためのガード（書き込み前に必ず判定する）
        SchemaVersionGuard.ensureJsonWritable(configFile).onLeft { reason ->
            logger.warning("config.jsonの保存を中止しました: $reason")
            return
        }

        // 書き込み時は常に現行スキーマ版数をスタンプする
        // （マイグレート済みの config.json が保存のたびにレガシー版数へ巻き戻るのを防ぐ）
        val stamped = config.copy(schemaVersion = SchemaVersions.CURRENT)
        val jsonString = Utils.json.encodeToString(stamped)

        // 書き込み中のクラッシュでconfig.jsonが壊れないよう、一時ファイルに書き込んでから
        // 原子的な置換で本体へ反映する。置換できない場合はcopyで代替せず、元のファイルには触れない
        // （copyは原子的ではなく、途中で失敗するとconfig.jsonが壊れた状態で残るため。
        //   設定の保存失敗で起動を止めるべきではないので、警告のみを出して続行する）
        AtomicFileWriter.write(configFile, jsonString).onLeft { reason ->
            logger.warning("config.jsonの保存に失敗しました（ファイルは変更していません）: $reason")
        }
    }
}
