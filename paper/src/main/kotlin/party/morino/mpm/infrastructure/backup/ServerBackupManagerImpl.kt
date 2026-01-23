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
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.model.backup.BackupReason
import party.morino.mpm.api.model.backup.RestoreResult
import party.morino.mpm.api.model.backup.ServerBackupInfo
import party.morino.mpm.utils.PluginDataUtils
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
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

    // インデックスファイル名
    private val indexFileName = "index.yaml"

    // 保持するバックアップの最大数（デフォルト値）
    private val maxBackups = 5

    /**
     * plugins/ディレクトリ全体のバックアップを作成する
     * @param reason バックアップを作成する理由
     * @return 成功時はServerBackupInfo、失敗時はエラーメッセージ
     */
    override suspend fun createBackup(reason: BackupReason): Either<String, ServerBackupInfo> =
        withContext(Dispatchers.IO) {
            try {
                val pluginsDir = pluginDirectory.getPluginsDirectory()
                val backupsDir = pluginDirectory.getBackupsDirectory()

                // バックアップIDとファイル名を生成
                val backupId = UUID.randomUUID().toString().substring(0, 8)
                val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss"))
                val fileName = "backup-$timestamp-$backupId.zip"
                val backupFile = File(backupsDir, fileName)

                // plugins/ディレクトリ内のプラグインを収集
                val pluginsIncluded = collectPluginNames(pluginsDir)

                // ZIPファイルを作成
                ZipOutputStream(FileOutputStream(backupFile)).use { zipOut ->
                    // plugins/ディレクトリ内のファイルとフォルダを走査
                    pluginsDir.listFiles()?.forEach { file ->
                        // MinecraftPluginManagerのディレクトリはスキップ（自分自身のバックアップを含めない）
                        if (file.name == "MinecraftPluginManager") {
                            return@forEach
                        }
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
                "バックアップの作成に失敗しました: ${e.message}".left()
            }
        }

    /**
     * 指定されたバックアップからplugins/ディレクトリをリストアする
     * @param backupId リストアするバックアップのID
     * @return 成功時はRestoreResult、失敗時はエラーメッセージ
     */
    override suspend fun restore(backupId: String): Either<String, RestoreResult> =
        withContext(Dispatchers.IO) {
            try {
                val backupsDir = pluginDirectory.getBackupsDirectory()
                val pluginsDir = pluginDirectory.getPluginsDirectory()
                val pluginsDirCanonical = pluginsDir.canonicalPath

                // インデックスからバックアップ情報を取得
                val index = loadIndex()
                val backupInfo =
                    index.backups.find { it.id == backupId }
                        ?: return@withContext "バックアップが見つかりません: $backupId".left()

                val backupFile = File(backupsDir, backupInfo.fileName)
                if (!backupFile.exists()) {
                    return@withContext "バックアップファイルが見つかりません: ${backupInfo.fileName}".left()
                }

                // リストア前に既存ファイルを削除（MinecraftPluginManagerディレクトリは除外）
                cleanupPluginsDir(pluginsDir)

                val restoredPlugins = mutableListOf<String>()
                val restoredConfigs = mutableListOf<String>()

                // ZIPファイルを展開
                ZipInputStream(FileInputStream(backupFile)).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        val targetFile = File(pluginsDir, entry.name)

                        // Zip Slip攻撃対策: 展開先がplugins/ディレクトリ内であることを確認
                        if (!targetFile.canonicalPath.startsWith(pluginsDirCanonical)) {
                            return@withContext "不正なZIPエントリが検出されました: ${entry.name}".left()
                        }

                        if (entry.isDirectory) {
                            // ディレクトリを作成
                            targetFile.mkdirs()
                            // 設定フォルダとして記録
                            val topLevelDir = entry.name.split("/").first()
                            if (!restoredConfigs.contains(topLevelDir) && !topLevelDir.endsWith(".jar")) {
                                restoredConfigs.add(topLevelDir)
                            }
                        } else {
                            // 親ディレクトリを作成
                            targetFile.parentFile?.mkdirs()
                            // ファイルを展開
                            FileOutputStream(targetFile).use { fos ->
                                zipIn.copyTo(fos)
                            }
                            // jarファイルとして記録
                            if (entry.name.endsWith(".jar")) {
                                restoredPlugins.add(entry.name)
                            }
                        }
                        entry = zipIn.nextEntry
                    }
                }

                RestoreResult(
                    backupId = backupId,
                    restoredPlugins = restoredPlugins,
                    restoredConfigs = restoredConfigs
                ).right()
            } catch (e: Exception) {
                "リストアに失敗しました: ${e.message}".left()
            }
        }

    /**
     * plugins/ディレクトリ内のファイルをクリーンアップする
     * MinecraftPluginManagerディレクトリは除外
     * @param pluginsDir plugins/ディレクトリ
     */
    private fun cleanupPluginsDir(pluginsDir: File) {
        pluginsDir.listFiles()?.forEach { file ->
            // MinecraftPluginManagerのディレクトリはスキップ
            if (file.name == "MinecraftPluginManager") {
                return@forEach
            }
            // ファイルまたはディレクトリを削除
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        }
    }

    /**
     * 存在するすべてのバックアップの一覧を取得する
     * @return 成功時はServerBackupInfoのリスト、失敗時はエラーメッセージ
     */
    override fun listBackups(): Either<String, List<ServerBackupInfo>> =
        try {
            val index = loadIndex()
            index.backups.sortedByDescending { it.createdAt }.right()
        } catch (e: Exception) {
            "バックアップ一覧の取得に失敗しました: ${e.message}".left()
        }

    /**
     * 指定されたバックアップを削除する
     * @param backupId 削除するバックアップのID
     * @return 成功時はUnit、失敗時はエラーメッセージ
     */
    override suspend fun deleteBackup(backupId: String): Either<String, Unit> =
        withContext(Dispatchers.IO) {
            try {
                val backupsDir = pluginDirectory.getBackupsDirectory()
                val index = loadIndex()

                val backupInfo =
                    index.backups.find { it.id == backupId }
                        ?: return@withContext "バックアップが見つかりません: $backupId".left()

                // バックアップファイルを削除
                val backupFile = File(backupsDir, backupInfo.fileName)
                if (backupFile.exists()) {
                    // 削除の成否を検証
                    if (!backupFile.delete()) {
                        return@withContext "バックアップファイルの削除に失敗しました: ${backupInfo.fileName}".left()
                    }
                }

                // インデックスから削除（ファイル削除が成功した後のみ）
                val updatedBackups = index.backups.filter { it.id != backupId }
                saveIndex(BackupIndex(updatedBackups))

                Unit.right()
            } catch (e: Exception) {
                "バックアップの削除に失敗しました: ${e.message}".left()
            }
        }

    /**
     * 設定に基づいて古いバックアップを削除する
     * @return 成功時は削除されたバックアップの数、失敗時はエラーメッセージ
     */
    override suspend fun cleanupOldBackups(): Either<String, Int> =
        withContext(Dispatchers.IO) {
            try {
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
                        { /* エラーの場合はスキップ */ },
                        { deletedCount++ }
                    )
                }

                deletedCount.right()
            } catch (e: Exception) {
                "古いバックアップの削除に失敗しました: ${e.message}".left()
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
            ?.filter { it.isFile && it.extension == "jar" && it.name != "MinecraftPluginManager.jar" }
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
}