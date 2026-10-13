/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import xyz.jpenilla.resourcefactory.velocity.velocityPluginJson

// Velocity プロキシ版の mpm。
// 共通ロジックは common モジュールにあり、ここには Velocity のメインクラス・プラットフォーム実装・イベントだけを置く。
plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    // velocity-plugin.json を Gradle で生成する（Kotlin のクラスには Velocity のアノテーションプロセッサが効かないため）
    alias(libs.plugins.resource.factory)
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.velocity)
}

group = "party.morino"
version = project.version.toString()

dependencies {
    // Paper / Velocity 共通のプラットフォーム非依存ロジック（api も推移的に含まれる）
    implementation(project(":common"))
    implementation(project(":api"))

    // Velocity API（実行時はプロキシが提供する）
    compileOnly(libs.velocity.api)

    // Kotlin の標準ライブラリ・kotlinx は JAR に同梱する
    // （他のプラグインから借りると、同名のクラスが別々のクラスローダーから読み込まれて LinkageError になるため）
    implementation(kotlin("stdlib"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlin.reflect)

    // Arrow / Koin（JARにバンドル）
    implementation(libs.arrow.core)
    implementation(libs.koin.core)

    // テスト依存関係（compileOnly はテストのクラスパスに乗らないため Velocity API を明示的に追加する）
    testImplementation(libs.velocity.api)
    testImplementation(libs.bundles.junit.common)
    testImplementation(libs.bundles.koin.test)
}

tasks {
    build {
        dependsOn(shadowJar)
    }
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            javaParameters.set(true) // パラメータ名を保持（Lamp必須）
        }
    }
    shadowJar {
        // 同じプロキシ上の他プラグインも Koin を使うため、GlobalContext などが共有されないよう移動する
        relocate("org.koin", "party.morino.mpm.libs.org.koin")
        mergeServiceFiles()
        // 依存ライブラリのライセンスファイルが JAR 直下で重複しないよう除外する
        exclude("META-INF/LICENSE", "META-INF/NOTICE")
        dependencies {
            // Velocity 本体が同じものを提供し、プラグインのクラスローダーは親（プロキシ）を優先するため同梱しない
            exclude(dependency("org.slf4j:.*:.*"))
            exclude(dependency("org.yaml:snakeyaml:.*"))
        }
    }
    runVelocity {
        // velocity-api と同じバージョンのプロキシで動作確認する
        velocityVersion(libs.versions.velocity.get())
    }
    test {
        useJUnitPlatform {
            // デフォルトのtestタスクではintegrationタグを除外する
            excludeTags("integration")
        }
        testLogging {
            showStandardStreams = true
            events("passed", "skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}

sourceSets.main {
    resourceFactory {
        // @Plugin アノテーションの代わりに velocity-plugin.json を生成する
        velocityPluginJson {
            // プラグインID（データディレクトリは plugins/mpm になる）
            id = "mpm"
            name = "mpm"
            // gradle.properties の version と連動する
            version = project.version.toString()
            description = "Minecraft plugin manager for Velocity"
            url = "https://mpm.plugin.morino.party"
            authors.add("morinoparty")
            main = "$group.mpm.velocity.MpmVelocity"
        }
    }
}