/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.plugin.scan

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import party.morino.mpm.api.domain.config.PluginDirectory
import party.morino.mpm.api.domain.downloader.model.TargetPlatform
import party.morino.mpm.api.domain.plugin.scan.InstalledJarScanner
import party.morino.mpm.api.domain.plugin.scan.model.InstalledJar
import party.morino.mpm.platform.SelfInfo
import party.morino.mpm.utils.PluginDataUtils
import java.io.File

/**
 * [InstalledJarScanner] の実装
 *
 * pluginsディレクトリ直下のJARを列挙し、plugin.yml / paper-plugin.yml / velocity-plugin.json から
 * プラグイン名とバージョンを読み取る。
 */
class InstalledJarScannerImpl :
    InstalledJarScanner,
    KoinComponent {
    // Koinによる依存性注入
    private val pluginDirectory: PluginDirectory by inject()

    // mpm 自身の除外と、記述子の優先順位の決定に使う
    private val selfInfo: SelfInfo by inject()
    private val targetPlatform: TargetPlatform by inject()

    override fun scan(): List<InstalledJar> =
        scanDirectory(pluginDirectory.getPluginsDirectory(), selfInfo.id, targetPlatform)

    internal companion object {
        /**
         * 指定ディレクトリ直下のJARを走査する（副作用のない純粋なロジック）
         *
         * DIに依存しないためテストから直接呼び出せる。
         *
         * @param pluginsDir 走査対象のディレクトリ
         * @param selfName 除外するMPM自身のプラグイン名
         * @param platform 動作中のプラットフォーム（複数の記述子を持つJARでどれを読むかを決める）
         * @return 走査で見つかったプラグインJARの一覧
         */
        internal fun scanDirectory(
            pluginsDir: File,
            selfName: String,
            platform: TargetPlatform
        ): List<InstalledJar> {
            // listFilesは非再帰なので、local/ などのサブディレクトリは自動的に対象外になる
            val jarFiles =
                pluginsDir.listFiles { file ->
                    file.isFile && file.extension == "jar"
                } ?: return emptyList()

            return jarFiles.mapNotNull { jarFile -> readInstalledJar(jarFile, selfName, platform) }
        }

        /**
         * 1つのJARからプラグイン情報を読み取る
         *
         * 解析できないJAR・名前が空のJAR・MPM自身のJARはnullを返す。
         */
        private fun readInstalledJar(
            jarFile: File,
            selfName: String,
            platform: TargetPlatform
        ): InstalledJar? {
            // 壊れたJARなどで例外が発生しても走査全体を止めない
            val pluginData =
                try {
                    PluginDataUtils.getPluginData(jarFile, platform)
                } catch (e: Exception) {
                    null
                }
            // プラグイン記述子を持たないJARは対象外
            if (pluginData == null) return null

            val name = pluginData.name
            val version = pluginData.version

            // 名前が空（空白のみを含む）のJARはPluginNameを構築できないため除外する
            if (name.isBlank()) return null
            // MPM自身は管理対象として扱わない（initの既存挙動に合わせて完全一致で比較）
            if (name == selfName) return null

            return InstalledJar(file = jarFile, name = name, version = version)
        }
    }
}