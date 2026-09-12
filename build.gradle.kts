/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.spotless)
    id("dev.detekt") version "2.0.0-alpha.6"
}

val version: String by project
group = "party.morino.mpm"

buildscript {
    repositories {
        mavenCentral()
    }
}

allprojects {

    apply(plugin = "java")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://oss.sonatype.org/content/groups/public/")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
        maven("https://jitpack.io")
        maven("https://plugins.gradle.org/m2/")
        maven("https://repo.codemc.io/repository/maven-public/")
    }

    kotlin {
        jvmToolchain {
            (this).languageVersion.set(JavaLanguageVersion.of(25))
        }
        jvmToolchain(25)
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        debug.set(true)
        ignoreFailures.set(true)
        filter {
            include("paper/**")
            include("api/**")
            exclude("**/config/**")
        }
    }

    tasks {
        register("hello") {
            doLast {
                println("I'm ${this.project.name}")
            }
        }
        compileKotlin {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
            compilerOptions.javaParameters = true
            compilerOptions.languageVersion.set(KotlinVersion.KOTLIN_2_0)
        }
        compileTestKotlin {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_25)
        }

        withType<JavaCompile>().configureEach {
            options.encoding = "UTF-8"
        }
    }
}

dependencies {
    dokka(project(":paper"))
    dokka(project(":api"))
}

dokka {
    pluginsConfiguration.html {
        footerMessage.set("No right reserved. This docs under CC0 1.0.")
    }
    dokkaPublications.html {
        // Next.jsが静的アセットとして配信するpublic/配下に出力する
        // これによりdocsサイトの /dokka/ パスからKotlin APIリファレンスにアクセス可能
        outputDirectory.set(file("${project.rootDir}/docs/public/dokka"))
    }
}

spotless {
    // ktlint / detekt と同様、当面は非ゲート（`check`/`build` を失敗させない）。
    // 開発者が任意に `./gradlew spotlessApply`（一括付与・更新）/ `spotlessCheck`（検証）を
    // 実行する運用とする。
    isEnforceCheck = false

    // ライセンスヘッダーは config/spotless/license-header.kt に一元管理し、$YEAR トークンで年を表す。
    // updateYearWithLatest により、既存の年を「開始年-現在年」の範囲へ更新する
    // （例: 2023 → 2023-2026）。新規ファイルは現在年のみ。全ファイルを対象にするため ratchet は使わない。
    val licenseHeader = rootProject.file("config/spotless/license-header.kt")
    kotlin {
        target("api/src/**/*.kt", "paper/src/**/*.kt")
        licenseHeaderFile(licenseHeader).updateYearWithLatest(true)
    }
    kotlinGradle {
        target("*.gradle.kts", "api/*.gradle.kts", "paper/*.gradle.kts")
        // .gradle.kts の最初の非ヘッダー行（build: import / settings: pluginManagement 等）を区切りとする。
        licenseHeaderFile(
            licenseHeader,
            "(import|plugins|pluginManagement|dependencyResolutionManagement|rootProject|@file)"
        ).updateYearWithLatest(true)
    }
}

detekt {
    // The directories where detekt looks for source files.
    // Defaults to `files("src/main/java", "src/test/java", "src/main/kotlin", "src/test/kotlin")`.
    source.setFrom("api/src/main/java", "api/src/main/kotlin", "paper/src/main/java", "paper/src/main/kotlin")

    // Builds the AST in parallel. Rules are always executed in parallel.
    // Can lead to speedups in larger projects. `false` by default.
    parallel = true

    // Applies the config files on top of detekt's default config file. `false` by default.
    buildUponDefaultConfig = true

    // Turns on all the rules. `false` by default.
    allRules = true

    // Specifying a baseline file. All findings stored in this file in subsequent runs of detekt.
    baseline = file("./detekt-baseline.xml")

    // Disables all default detekt rulesets and will only run detekt with custom rules
    // defined in plugins passed in with `detektPlugins` configuration. `false` by default.
    disableDefaultRuleSets = false

    // Adds debug output during task execution. `false` by default.
    debug = false

    // If set to `true` the build does not fail when there are any issues.
    // Defaults to `false`.
    ignoreFailures = true
}