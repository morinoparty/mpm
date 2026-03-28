/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related
 * and neighboring rights to this software to the public domain worldwide.
 * This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.backup

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@DisplayName("Backup ZIP validation tests")
class BackupZipValidationTest {
    @TempDir
    lateinit var tempDir: File

    private lateinit var pluginsDir: File

    @BeforeEach
    fun setup() {
        pluginsDir = File(tempDir, "plugins")
        pluginsDir.mkdirs()
    }

    @Test
    @DisplayName("Rejects ZIP entry with path traversal (Zip Slip)")
    fun testRejectsZipSlipEntry() {
        // Zip Slip攻撃を含むZIPファイルを作成
        val maliciousZip = File(tempDir, "malicious.zip")
        ZipOutputStream(FileOutputStream(maliciousZip)).use { zos ->
            // 正常なエントリ
            zos.putNextEntry(ZipEntry("normal-plugin.jar"))
            zos.write("normal content".toByteArray())
            zos.closeEntry()

            // 悪意のあるパストラバーサルエントリ
            zos.putNextEntry(ZipEntry("../../../etc/malicious.txt"))
            zos.write("malicious content".toByteArray())
            zos.closeEntry()
        }

        // ZIPの全エントリを検証
        val pluginsDirPrefix = pluginsDir.canonicalPath + File.separator
        var hasInvalidEntry = false

        ZipInputStream(FileInputStream(maliciousZip)).use { zipIn ->
            var entry: ZipEntry? = zipIn.nextEntry
            while (entry != null) {
                val targetFile = File(pluginsDir, entry.name)
                if (!targetFile.canonicalPath.startsWith(pluginsDirPrefix) &&
                    targetFile.canonicalPath != pluginsDir.canonicalPath
                ) {
                    hasInvalidEntry = true
                    break
                }
                entry = zipIn.nextEntry
            }
        }

        assertTrue(hasInvalidEntry, "Should detect path traversal entry")
    }

    @Test
    @DisplayName("Accepts valid ZIP entries within plugins directory")
    fun testAcceptsValidEntries() {
        // 正常なZIPファイルを作成
        val validZip = File(tempDir, "valid.zip")
        ZipOutputStream(FileOutputStream(validZip)).use { zos ->
            zos.putNextEntry(ZipEntry("plugin-a.jar"))
            zos.write("plugin content".toByteArray())
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("PluginConfig/"))
            zos.closeEntry()

            zos.putNextEntry(ZipEntry("PluginConfig/config.yml"))
            zos.write("key: value".toByteArray())
            zos.closeEntry()
        }

        // ZIPの全エントリを検証
        val pluginsDirPrefix = pluginsDir.canonicalPath + File.separator
        var hasInvalidEntry = false

        ZipInputStream(FileInputStream(validZip)).use { zipIn ->
            var entry: ZipEntry? = zipIn.nextEntry
            while (entry != null) {
                val targetFile = File(pluginsDir, entry.name)
                if (!targetFile.canonicalPath.startsWith(pluginsDirPrefix) &&
                    targetFile.canonicalPath != pluginsDir.canonicalPath
                ) {
                    hasInvalidEntry = true
                    break
                }
                entry = zipIn.nextEntry
            }
        }

        assertFalse(hasInvalidEntry, "Should accept all valid entries")
    }

    @Test
    @DisplayName("Rejects sibling directory traversal with File.separator check")
    fun testRejectsSiblingDirectoryTraversal() {
        // plugins-malicious のような兄弟ディレクトリへの攻撃
        // File.separator チェックなしだと "plugins" がプレフィックスとして通ってしまう
        val siblingDir = File(tempDir, "plugins-malicious")
        siblingDir.mkdirs()

        val siblingZip = File(tempDir, "sibling.zip")
        ZipOutputStream(FileOutputStream(siblingZip)).use { zos ->
            zos.putNextEntry(ZipEntry("../plugins-malicious/evil.jar"))
            zos.write("evil content".toByteArray())
            zos.closeEntry()
        }

        val pluginsDirPrefix = pluginsDir.canonicalPath + File.separator
        var hasInvalidEntry = false

        ZipInputStream(FileInputStream(siblingZip)).use { zipIn ->
            var entry: ZipEntry? = zipIn.nextEntry
            while (entry != null) {
                val targetFile = File(pluginsDir, entry.name)
                if (!targetFile.canonicalPath.startsWith(pluginsDirPrefix) &&
                    targetFile.canonicalPath != pluginsDir.canonicalPath
                ) {
                    hasInvalidEntry = true
                    break
                }
                entry = zipIn.nextEntry
            }
        }

        assertTrue(hasInvalidEntry, "Should detect sibling directory traversal")
    }
}
