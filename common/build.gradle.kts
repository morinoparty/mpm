/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import org.gradle.api.tasks.testing.logging.TestExceptionFormat

// Paper / Velocity で共有する、プラットフォーム非依存のロジックを置くモジュール。
// org.bukkit / io.papermc / com.velocitypowered には依存しない。
// プラットフォーム固有の処理は party.morino.mpm.platform 配下のインターフェースを介して各モジュールが実装する。
plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

group = "party.morino"
version = project.version.toString()

dependencies {
    // API module dependency（common の公開シグネチャに api の型が現れるため api で公開する）
    api(project(":api"))

    // Paper / Velocity の双方が実行時に提供するため compileOnly で参照する
    compileOnly(libs.slf4j.api)
    compileOnly(libs.bundles.adventure)

    // Arrow for functional programming
    implementation(libs.arrow.core)
    implementation(libs.arrow.fx.coroutines)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kaml)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)

    // Koin for dependency injection
    implementation(libs.koin.core)

    // Apache Commons IO
    implementation(libs.commons.io)

    // Ktor client for HTTP requests
    implementation(libs.bundles.ktor.client)

    // Kotlin reflection
    implementation(libs.kotlin.reflect)

    // SnakeYAML
    implementation(libs.snakeyaml)

    // Cron expression parser
    implementation(libs.cron.utils)

    // compileOnly はテストのクラスパスに載らないため、テストでは明示的に追加する
    testImplementation(libs.slf4j.api)
    testImplementation(libs.bundles.adventure)

    testImplementation(libs.bundles.junit.common)
    testImplementation(libs.bundles.koin.test)
    testImplementation(libs.bundles.ktor.client)
    testImplementation(libs.commons.io)

    // Allureレポート用（JUnit5の実行結果をallure-resultsとして出力する）
    testImplementation(libs.allure.junit5)
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            javaParameters.set(true) // パラメータ名を保持（Lamp必須）
        }
    }
    test {
        useJUnitPlatform {
            // デフォルトのtestタスクではintegrationタグを除外する
            // （実APIを叩くテストはレート制限・一時的障害で不安定になるため）
            excludeTags("integration")
        }
        // Allure結果の出力先を指定（CIでレポート生成する際に集約する）
        systemProperty("allure.results.directory", "${project.layout.buildDirectory.get().asFile}/allure-results")
        // allure-resultsはsystemPropertyによる副作用出力のためGradleに出力として明示しないと
        // ビルドキャッシュ復元時にディレクトリが復元されない
        outputs.dir(project.layout.buildDirectory.dir("allure-results"))
        testLogging {
            showStandardStreams = true
            events("passed", "skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    // 実APIを叩くインテグレーションテスト専用タスク（opt-in実行）
    // 実行例: ./gradlew :common:integrationTest
    register<Test>("integrationTest") {
        description = "Runs integration tests that hit real upstream APIs (opt-in)."
        group = "verification"

        // testと同じクラスパス・ソースセットを利用
        testClassesDirs =
            sourceSets.test
                .get()
                .output.classesDirs
        classpath = sourceSets.test.get().runtimeClasspath

        useJUnitPlatform {
            includeTags("integration")
        }
        testLogging {
            showStandardStreams = true
            events("passed", "skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }

        // 通常のtest実行後に走らせる（並列実行時の順序保証）
        shouldRunAfter("test")
    }
}