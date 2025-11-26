/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm

import be.seeseemelk.mockbukkit.MockBukkit
import be.seeseemelk.mockbukkit.ServerMock
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import party.morino.mpm.api.config.PluginDirectory
import party.morino.mpm.mock.config.PluginDirectoryMock
import kotlin.jvm.java

class MinecraftPluginManagerTest :
    BeforeEachCallback,
    AfterEachCallback {
    private lateinit var server: ServerMock
    private lateinit var plugin: MinecraftPluginManager

    override fun beforeEach(context: ExtensionContext) {
        println("beforeEach() executed before " + context.displayName + ".")
        server = MockBukkit.mock()
        setupKoin()
    }

    override fun afterEach(context: ExtensionContext) {
        MockBukkit.unmock()
        stopKoin()
    }

    private fun setupKoin() {
        // プラグインをロード（この時点でonEnable()が呼ばれてKoinが初期化される）
        plugin = MockBukkit.load(MinecraftPluginManager::class.java)

        // プラグイン側で初期化されたKoinを停止
        stopKoin()

        // テスト用のモジュールを定義
        val appModule =
            module {
                single<MinecraftPluginManager> { plugin }
                single<ServerMock> { server }
                single<PluginDirectory> { PluginDirectoryMock() }

                // リポジトリの登録
                single<party.morino.mpm.api.core.plugin.DownloaderRepository> {
                    party.morino.mpm.core.plugin
                        .DownloaderRepositoryImpl()
                }
                single<party.morino.mpm.api.core.plugin.PluginRepository> {
                    party.morino.mpm.core.plugin
                        .PluginRepositoryImpl()
                }

                // ユースケースの登録
                single<party.morino.mpm.api.core.plugin.InitUseCase> {
                    party.morino.mpm.core.plugin
                        .InitUseCaseImpl()
                }
                single<party.morino.mpm.api.core.plugin.PluginInstallUseCase> {
                    party.morino.mpm.core.plugin
                        .PluginInstallUseCaseImpl()
                }
                single<party.morino.mpm.api.core.plugin.PluginListUseCase> {
                    party.morino.mpm.core.plugin
                        .PluginListUseCaseImpl()
                }
            }

        // テスト用のモジュールでKoinを初期化
        GlobalContext.startKoin {
            modules(appModule)
        }
    }
}