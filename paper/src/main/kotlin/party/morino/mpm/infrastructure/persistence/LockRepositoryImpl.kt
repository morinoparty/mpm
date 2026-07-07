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

import com.charleskorn.kaml.Yaml
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.project.lock.LockRepository
import party.morino.mpm.api.domain.project.lock.MpmLock
import java.io.File

/**
 * [LockRepository] の実装
 *
 * mpm-lock.yaml を mpm.json と同じルートディレクトリに読み書きする。
 * 書き込みは一時ファイル + rename でアトミックに行い、クラッシュ時の破損を防ぐ。
 */
class LockRepositoryImpl :
    LockRepository,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()

    override suspend fun find(): MpmLock? {
        val lockFile = getLockFile()
        if (!lockFile.exists()) {
            return null
        }
        return try {
            val yamlString = lockFile.readText()
            Yaml.default.decodeFromString(MpmLock.serializer(), yamlString)
        } catch (e: Exception) {
            // パース失敗時はnull（破損したロックは無視して再生成に委ねる）
            null
        }
    }

    override suspend fun save(lock: MpmLock) {
        val lockFile = getLockFile()
        val yamlString = Yaml.default.encodeToString(MpmLock.serializer(), lock)

        // 一時ファイルへ書き込んでから rename（アトミック）で反映する
        val tempFile = File(lockFile.parentFile, "${lockFile.name}.tmp")
        tempFile.writeText(yamlString)
        if (!tempFile.renameTo(lockFile)) {
            tempFile.copyTo(lockFile, overwrite = true)
            tempFile.delete()
        }
    }

    override suspend fun exists(): Boolean = getLockFile().exists()

    /**
     * mpm-lock.yaml ファイルのパスを取得する（mpm.json と同じルートディレクトリ）
     */
    private fun getLockFile(): File {
        val rootDir = pluginDirectory.getRootDirectory()
        return File(rootDir, "mpm-lock.yaml")
    }
}