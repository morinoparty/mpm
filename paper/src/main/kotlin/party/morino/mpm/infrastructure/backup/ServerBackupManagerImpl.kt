/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.backup

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.charleskorn.kaml.Yaml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.domain.backup.ServerBackupManager
import party.morino.mpm.api.domain.config.ConfigManager
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.model.backup.BackupReason
import party.morino.mpm.api.model.backup.BackupSizeEntry
import party.morino.mpm.api.model.backup.BackupSizeInfo
import party.morino.mpm.api.model.backup.RestoreResult
import party.morino.mpm.api.model.backup.ServerBackupInfo
import party.morino.mpm.api.shared.error.MpmError
import party.morino.mpm.utils.PluginDataUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.logging.Logger
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * バックアップインデックスを保存するためのデータクラス
 * @property backups バックアップ情報のリスト
 */
@Serializable
data class BackupIndex(
    val backups: List<ServerBackupInfo> = emptyList()
)

/**
 * サーバー全体のバックアップを管理する実装クラス
 * plugins/ディレクトリ全体（jarファイル + 設定フォルダ）をZIP形式でバックアップする
 */
class ServerBackupManagerImpl :
    ServerBackupManager,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()
    private val configManager: ConfigManager by inject()
    private val logger: Logger = Logger.getLogger(ServerBackupManagerImpl::class.java.name)

    // 自プラグインのデータフォルダ名（バックアップ対象から除外するため）
    private val selfDirName: String by lazy { pluginDirectory.getRootDirectory().name }

    // インデックスファイル名
    private val indexFileName = "index.yaml"

    /**
     * plugins/ディレクトリ全体のバックアップを作成する
     * @param reason バックアップを作成する理由
     * @return 成功時はServerBackupInfo、失敗時はMpmError.BackupError
     */
    override suspend fun createBackup(reason: BackupReason): Either<MpmError, ServerBackupInfo> =
        withContext(Dispatchers.IO) {
            try {
                // バックアップ設定を取得
                val backupSettings = configManager.getConfig().settings.backup

                // マスタースイッチが無効な場合はバックアップを作成しない
                if (!backupSettings.enabled) {
                    return@withContext MpmError.BackupError.Failed("バックアップ機能が無効化されています").left()
                }

                // update/install起因の自動バックアップは、それぞれの設定が無効な場合はスキップする
                // （手動バックアップはマスタースイッチのみに従う）
                when (reason) {
                    BackupReason.UPDATE ->
                        if (!backupSettings.autoBackupOnUpdate) {
                            return@withContext MpmError.BackupError.Failed("update時の自動バックアップは無効化されています").left()
                        }
                    BackupReason.INSTALL ->
                        if (!backupSettings.autoBackupOnInstall) {
                            return@withContext MpmError.BackupError.Failed("install時の自動バックアップは無効化されています").left()
                        }
                    BackupReason.MANUAL -> Unit
                }

                val pluginsDir = pluginDirectory.getPluginsDirectory()
                val backupsDir = pluginDirectory.getBackupsDirectory()

                // バックアップIDとファイル名を生成
                val backupId = UUID.randomUUID().toString().substring(0, 8)
                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"))
                val fileName = "backup-$timestamp-$backupId.zip"
                val backupFile = File(backupsDir, fileName)

                // plugins/ディレクトリ内のプラグインを収集
                val pluginsIncluded = collectPluginNames(pluginsDir)

                // .mpmignore パターンを読み込む
                val ignorePatterns = readIgnorePatterns()

                // ZIPファイルを作成
                ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->
                    // plugins/ディレクトリ内のファイルとフォルダを走査
                    pluginsDir.listFiles()?.forEach { file ->
                        // 自プラグインのディレクトリはスキップ
                        if (file.name == selfDirName) return@forEach
                        // .mpmignore にマッチするエントリはスキップ
                        if (isIgnored(file.name, ignorePatterns)) return@forEach
                        addToZip(zipOut, file, file.name)
                    }
                }

                // バックアップ情報を作成
                val backupInfo =
                    ServerBackupInfo(
                        id = backupId,
                        createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        reason = reason,
                        fileName = fileName,
                        pluginsIncluded = pluginsIncluded,
                        sizeBytes = backupFile.length()
                    )

                // インデックスを更新
                updateIndex(backupInfo)

                backupInfo.right()
            } catch (e: Exception) {
                MpmError.BackupError.Failed("バックアップの作成に失敗しました: ${e.message}").left()
            }
        }

    /**
     * 指定されたバックアップからplugins/ディレクトリをリストアする
     * @param backupId リストアするバックアップのID
     * @return 成功時はRestoreResult、失敗時はMpmError.BackupError
     */
    override suspend fun restore(backupId: String): Either<MpmError, RestoreResult> =
        withContext(Dispatchers.IO) {
            try {
                val backupsDir = pluginDirectory.getBackupsDirectory()
                val pluginsDir = pluginDirectory.getPluginsDirectory()
                // Zip Slip対策: セパレータ付きプレフィックスで兄弟ディレクトリ攻撃を防止
                val pluginsDirPrefix = pluginsDir.canonicalPath + File.separator

                // インデックスからバックアップ情報を取得
                val index = loadIndex()
                val backupInfo =
                    index.backups.find { it.id == backupId }
                        ?: return@withContext MpmError.BackupError.NotFound(backupId).left()

                val backupFile = File(backupsDir, backupInfo.fileName)
                if (!backupFile.exists()) {
                    return@withContext MpmError.BackupError
                        .RestoreFailed(
                            "バックアップファイルが見つかりません: ${backupInfo.fileName}"
                        ).left()
                }

                // Phase 1: ZIPエントリを事前検証（破壊的操作の前にすべてのパスを確認）
                ZipInputStream(FileInputStream(backupFile)).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        val targetFile = File(pluginsDir, entry.name)
                        if (!targetFile.canonicalPath.startsWith(pluginsDirPrefix) &&
                            targetFile.canonicalPath != pluginsDir.canonicalPath
                        ) {
                            return@withContext MpmError.BackupError
                                .RestoreFailed(
                                    "不正なZIPエントリが検出されました: ${entry.name}"
                                ).left()
                        }
                        entry = zipIn.nextEntry
                    }
                }

                // Phase 2: 一時ディレクトリに展開（pluginsディレクトリをまだ削除しない）
                val tempDir = File(pluginsDir.parentFile, ".mpm-restore-${System.currentTimeMillis()}")
                tempDir.mkdirs()

                val restoredPlugins = mutableListOf<String>()
                val restoredConfigs = mutableListOf<String>()
                // 一時ディレクトリのプレフィックス
                val tempDirPrefix = tempDir.canonicalPath + File.separator

                try {
                    ZipInputStream(FileInputStream(backupFile)).use { zipIn ->
                        var entry: ZipEntry? = zipIn.nextEntry
                        while (entry != null) {
                            val targetFile = File(tempDir, entry.name)

                            // 一時ディレクトリ内であることを再確認
                            if (!targetFile.canonicalPath.startsWith(tempDirPrefix) &&
                                targetFile.canonicalPath != tempDir.canonicalPath
                            ) {
                                return@withContext MpmError.BackupError
                                    .RestoreFailed(
                                        "不正なZIPエントリが検出されました: ${entry.name}"
                                    ).left()
                            }

                            if (entry.isDirectory) {
                                targetFile.mkdirs()
                                val topLevelDir = entry.name.split("/").first()
                                if (!restoredConfigs.contains(topLevelDir) && !topLevelDir.endsWith(".jar")) {
                                    restoredConfigs.add(topLevelDir)
                                }
                            } else {
                                targetFile.parentFile?.mkdirs()
                                FileOutputStream(targetFile).use { fos ->
                                    zipIn.copyTo(fos)
                                }
                                if (entry.name.endsWith(".jar")) {
                                    restoredPlugins.add(entry.name)
                                }
                            }
                            entry = zipIn.nextEntry
                        }
                    }

                    // Phase 3: 展開成功後にリネームベースでディレクトリを安全にスワップ
                    val backupDir = File(pluginsDir.parentFile, ".mpm-plugins-backup-${System.currentTimeMillis()}")

                    // 自プラグインのディレクトリを一時ディレクトリにコピー（復元対象外のため保持）
                    val mpmDir = File(pluginsDir, selfDirName)
                    if (mpmDir.exists()) {
                        mpmDir.copyRecursively(File(tempDir, selfDirName), overwrite = true)
                    }

                    // pluginsディレクトリをバックアップ名にリネーム
                    if (!pluginsDir.renameTo(backupDir)) {
                        return@withContext MpmError.BackupError.RestoreFailed("pluginsディレクトリのリネームに失敗しました").left()
                    }

                    // 一時ディレクトリをpluginsディレクトリにリネーム
                    if (!tempDir.renameTo(pluginsDir)) {
                        // 失敗時はバックアップを復元
                        backupDir.renameTo(pluginsDir)
                        return@withContext MpmError.BackupError.RestoreFailed("復元ディレクトリのリネームに失敗しました").left()
                    }

                    // スワップ成功後に旧pluginsディレクトリを削除
                    backupDir.deleteRecursively()
                } finally {
                    // tempDirのクリーンアップ。
                    // 展開・スワップが正常に完了した場合、tempDirは既にpluginsDirへリネーム済みで
                    // 実体が存在しないため、この呼び出しは何もしない安全なno-opとなる。
                    // ZIPエントリ不正・リネーム失敗などで早期returnした場合は、
                    // 残存する一時ディレクトリ（プラグインjarや設定フォルダのコピーを含む）を確実に削除する。
                    tempDir.deleteRecursively()
                }

                RestoreResult(
                    backupId = backupId,
                    restoredPlugins = restoredPlugins,
                    restoredConfigs = restoredConfigs
                ).right()
            } catch (e: Exception) {
                MpmError.BackupError.RestoreFailed("リストアに失敗しました: ${e.message}").left()
            }
        }

    /**
     * 存在するすべてのバックアップの一覧を取得する
     * @return 成功時はServerBackupInfoのリスト、失敗時はMpmError.BackupError
     */
    override fun listBackups(): Either<MpmError, List<ServerBackupInfo>> =
        try {
            val index = loadIndex()
            index.backups.sortedByDescending { it.createdAt }.right()
        } catch (e: Exception) {
            MpmError.BackupError.Failed("バックアップ一覧の取得に失敗しました: ${e.message}").left()
        }

    /**
     * 指定されたバックアップを削除する
     * @param backupId 削除するバックアップのID
     * @return 成功時はUnit、失敗時はMpmError.BackupError
     */
    override suspend fun deleteBackup(backupId: String): Either<MpmError, Unit> =
        withContext(Dispatchers.IO) {
            try {
                val backupsDir = pluginDirectory.getBackupsDirectory()
                val index = loadIndex()

                val backupInfo =
                    index.backups.find { it.id == backupId }
                        ?: return@withContext MpmError.BackupError.NotFound(backupId).left()

                // バックアップファイルを削除
                val backupFile = File(backupsDir, backupInfo.fileName)
                if (backupFile.exists()) {
                    // 削除の成否を検証
                    if (!backupFile.delete()) {
                        return@withContext MpmError.BackupError
                            .Failed(
                                "バックアップファイルの削除に失敗しました: ${backupInfo.fileName}"
                            ).left()
                    }
                }

                // インデックスから削除（ファイル削除が成功した後のみ）
                val updatedBackups = index.backups.filter { it.id != backupId }
                saveIndex(BackupIndex(updatedBackups))

                Unit.right()
            } catch (e: Exception) {
                MpmError.BackupError.Failed("バックアップの削除に失敗しました: ${e.message}").left()
            }
        }

    /**
     * 設定に基づいて古いバックアップを削除する
     * @return 成功時は削除されたバックアップの数、失敗時はMpmError.BackupError
     */
    override suspend fun cleanupOldBackups(): Either<MpmError, Int> =
        withContext(Dispatchers.IO) {
            try {
                // 設定から保持するバックアップの最大数を取得
                val maxBackups =
                    configManager
                        .getConfig()
                        .settings.backup.maxBackups
                val index = loadIndex()
                val sortedBackups = index.backups.sortedByDescending { it.createdAt }

                // 最大数を超えるバックアップを削除
                if (sortedBackups.size <= maxBackups) {
                    return@withContext 0.right()
                }

                val backupsToDelete = sortedBackups.drop(maxBackups)
                var deletedCount = 0

                for (backup in backupsToDelete) {
                    deleteBackup(backup.id).fold(
                        { error -> logger.warning("バックアップ ${backup.id} の削除に失敗: ${error.message}") },
                        { deletedCount++ }
                    )
                }

                deletedCount.right()
            } catch (e: Exception) {
                MpmError.BackupError.Failed("古いバックアップの削除に失敗しました: ${e.message}").left()
            }
        }

    /**
     * plugins/ディレクトリからプラグイン名を収集する
     * @param pluginsDir plugins/ディレクトリ
     * @return プラグイン名のリスト
     */
    private fun collectPluginNames(pluginsDir: File): List<String> =
        pluginsDir
            .listFiles()
            ?.filter { it.isFile && it.extension == "jar" && it.name != "$selfDirName.jar" }
            ?.mapNotNull { jarFile ->
                try {
                    // jarファイルからプラグイン名を取得
                    PluginDataUtils.getPluginData(jarFile)?.let { pluginData ->
                        when (pluginData) {
                            is party.morino.mpm.api.model.plugin.PluginData.BukkitPluginData -> pluginData.name
                            is party.morino.mpm.api.model.plugin.PluginData.PaperPluginData -> pluginData.name
                        }
                    }
                } catch (e: Exception) {
                    // プラグインデータの取得に失敗した場合はファイル名を使用
                    jarFile.nameWithoutExtension
                }
            }
            ?: emptyList()

    /**
     * ファイルまたはディレクトリをZIPに追加する（再帰的）
     * @param zipOut ZIPストリーム
     * @param file 追加するファイルまたはディレクトリ
     * @param entryName ZIPエントリ名
     */
    private fun addToZip(
        zipOut: ZipOutputStream,
        file: File,
        entryName: String
    ) {
        if (file.isDirectory) {
            // ディレクトリ自体のエントリを書き込む（名前の末尾に"/"を付与するとZipEntry.isDirectory()がtrueになる）
            zipOut.putNextEntry(ZipEntry("$entryName/"))
            zipOut.closeEntry()

            // ディレクトリの場合は子要素を再帰的に追加
            val children = file.listFiles() ?: return
            for (child in children) {
                addToZip(zipOut, child, "$entryName/${child.name}")
            }
        } else {
            // ファイルの場合はZIPエントリとして追加
            FileInputStream(file).use { fis ->
                val entry = ZipEntry(entryName)
                zipOut.putNextEntry(entry)
                fis.copyTo(zipOut)
                zipOut.closeEntry()
            }
        }
    }

    /**
     * インデックスファイルを読み込む
     * @return BackupIndex
     */
    private fun loadIndex(): BackupIndex {
        val backupsDir = pluginDirectory.getBackupsDirectory()
        val indexFile = File(backupsDir, indexFileName)

        return if (indexFile.exists()) {
            try {
                val yamlString = indexFile.readText()
                Yaml.default.decodeFromString(BackupIndex.serializer(), yamlString)
            } catch (e: Exception) {
                // インデックスファイルの破損を警告し、空のインデックスで続行する
                logger.warning("バックアップインデックスの読み込みに失敗しました（${indexFile.absolutePath}）: ${e.message}")
                BackupIndex()
            }
        } else {
            BackupIndex()
        }
    }

    /**
     * インデックスファイルを保存する
     * @param index 保存するBackupIndex
     */
    private fun saveIndex(index: BackupIndex) {
        val backupsDir = pluginDirectory.getBackupsDirectory()
        val indexFile = File(backupsDir, indexFileName)
        val yamlString = Yaml.default.encodeToString(BackupIndex.serializer(), index)
        indexFile.writeText(yamlString)
    }

    /**
     * インデックスに新しいバックアップ情報を追加する
     * @param backupInfo 追加するバックアップ情報
     */
    private fun updateIndex(backupInfo: ServerBackupInfo) {
        val index = loadIndex()
        val updatedBackups = index.backups + backupInfo
        saveIndex(BackupIndex(updatedBackups))
    }

    /**
     * バックアップ対象のサイズを計算する
     *
     * @return 成功時はBackupSizeInfo、失敗時はMpmError.BackupError
     */
    override fun calculateBackupSize(): Either<MpmError, BackupSizeInfo> =
        try {
            val pluginsDir = pluginDirectory.getPluginsDirectory()
            val ignorePatterns = readIgnorePatterns()

            val entries = mutableListOf<BackupSizeEntry>()
            val excludedEntries = mutableListOf<String>()

            pluginsDir.listFiles()?.forEach { file ->
                when {
                    // 自プラグインのディレクトリは除外
                    file.name == selfDirName -> excludedEntries.add(file.name)
                    // .mpmignore ファイル自体は除外
                    file.name == ".mpmignore" -> excludedEntries.add(file.name)
                    // ignoreパターンにマッチするエントリは除外
                    isIgnored(file.name, ignorePatterns) -> excludedEntries.add(file.name)
                    else -> {
                        val size = file.totalSizeBytes()
                        val count = file.fileCount()
                        entries.add(BackupSizeEntry(name = file.name, sizeBytes = size, fileCount = count))
                    }
                }
            }

            val totalSize = entries.sumOf { it.sizeBytes }
            val totalCount = entries.sumOf { it.fileCount }

            BackupSizeInfo(
                totalSizeBytes = totalSize,
                totalFileCount = totalCount,
                entries = entries.sortedByDescending { it.sizeBytes },
                excludedEntries = excludedEntries
            ).right()
        } catch (e: Exception) {
            MpmError.BackupError.Failed("サイズ計算に失敗しました: ${e.message}").left()
        }

    /**
     * plugins/.mpmignore からignoreパターンを読み込む
     *
     * @return パターンのリスト（コメント・空行を除く）
     */
    override fun readIgnorePatterns(): List<String> {
        val ignoreFile = File(pluginDirectory.getPluginsDirectory(), ".mpmignore")
        if (!ignoreFile.exists()) return emptyList()
        return ignoreFile
            .readLines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
    }

    /**
     * ファイル名がignoreパターンのいずれかにマッチするか判定する
     *
     * パターン記法:
     * - `*`  : 任意の文字列（/ を除く）にマッチ
     * - `?`  : 任意の1文字にマッチ
     * - その他: 完全一致
     *
     * @param name 判定するファイル・ディレクトリ名
     * @param patterns ignoreパターンのリスト
     * @return いずれかのパターンにマッチする場合はtrue
     */
    private fun isIgnored(
        name: String,
        patterns: List<String>
    ): Boolean = patterns.any { pattern -> globMatches(pattern, name) }

    /**
     * 単純なglobパターンマッチングを行う
     * `*` は任意の文字列、`?` は任意の1文字に対応する
     *
     * @param pattern globパターン
     * @param name マッチ対象の文字列
     * @return マッチする場合はtrue
     */
    private fun globMatches(
        pattern: String,
        name: String
    ): Boolean {
        // パターンをRegexに変換（* → .*, ? → .）
        val regex =
            buildString {
                for (ch in pattern) {
                    when (ch) {
                        '*' -> append(".*")
                        '?' -> append(".")
                        '.', '(', ')', '[', ']', '{', '}', '^', '$', '+', '|', '\\' -> {
                            append('\\')
                            append(ch)
                        }
                        else -> append(ch)
                    }
                }
            }
        return Regex("^$regex$").matches(name)
    }

    /**
     * ファイルまたはディレクトリの合計バイト数を返す
     */
    private fun File.totalSizeBytes(): Long =
        if (isFile) {
            length()
        } else {
            walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }

    /**
     * ファイルまたはディレクトリ内のファイル数を返す
     */
    private fun File.fileCount(): Int =
        if (isFile) {
            1
        } else {
            walkTopDown().count { it.isFile }
        }
}