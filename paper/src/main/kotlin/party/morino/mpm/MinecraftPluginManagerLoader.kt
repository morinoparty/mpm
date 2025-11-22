/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm

import io.papermc.paper.plugin.loader.PluginClasspathBuilder
import io.papermc.paper.plugin.loader.PluginLoader
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.RemoteRepository

/**
 * MinecraftPluginManagerのローダークラス
 * プラグインの依存関係を管理するためのクラス
 */
@Suppress("unused")
class MinecraftPluginManagerLoader : PluginLoader {
    /**
     * プラグインのクラスローダーに依存関係を追加するメソッド
     * @param classpathBuilder クラスパスビルダー
     */
    override fun classloader(classpathBuilder: PluginClasspathBuilder) {
        // Maven依存関係リゾルバーの作成
        val resolver = MavenLibraryResolver()

        // Kotlin標準ライブラリの追加
        resolver.addDependency(Dependency(DefaultArtifact("org.jetbrains.kotlin:kotlin-stdlib:2.1.0"), null))

        // Mavenリポジトリの追加
        resolver.addRepository(
            RemoteRepository.Builder("paper", "default", "https://repo.papermc.io/repository/maven-public/").build()
        )
        resolver.addRepository(
            RemoteRepository.Builder("maven-central", "default", "https://repo1.maven.org/maven2/").build()
        )

        // クラスパスに追加
        classpathBuilder.addLibrary(resolver)
    }
}