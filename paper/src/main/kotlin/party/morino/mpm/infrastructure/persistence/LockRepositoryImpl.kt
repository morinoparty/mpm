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
import party.morino.mpm.infrastructure.migration.AtomicFileWriter
import java.io.File
import java.io.IOException

/**
 * [LockRepository] の実装
 *
 * mpm-lock.yaml を mpm.json と同じルートディレクトリに読み書きする。
 * 書き込みは他の設定ファイルと同じく [AtomicFileWriter] に委ね、
 * 一意な一時ファイル + 原子的な move で反映する（クラッシュ時・同時書き込み時の破損を防ぐ）。
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

        // 原子的に置き換えられない場合は copy で代替せず失敗させる（元ファイルを壊さないため）。
        // 呼び出し側（LockServiceImpl）は例外を SaveFailed に変換するので、例外で伝播させる。
        AtomicFileWriter.write(lockFile, yamlString).onLeft { reason ->
            throw IOException(reason)
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