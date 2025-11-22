import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ktlint)
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
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
            (this).languageVersion.set(JavaLanguageVersion.of(21))
        }
        jvmToolchain(21)
    }

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        debug.set(true)
        ignoreFailures.set(true)
        filter {
            include("app/**")
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
            compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
            compilerOptions.javaParameters = true
            compilerOptions.languageVersion.set(KotlinVersion.KOTLIN_2_0)
        }
        compileTestKotlin {
            compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
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
        outputDirectory.set(file("${project.rootDir}/docs/static/dokka"))
    }
}

detekt {
    // Version of detekt that will be used. When unspecified the latest detekt
    // version found will be used. Override to stay on the same version.
    toolVersion = "1.23.8"

    // The directories where detekt looks for source files.
    // Defaults to `files("src/main/java", "src/test/java", "src/main/kotlin", "src/test/kotlin")`.
    source.setFrom("api/src/main/java", "api/src/main/kotlin", "app/src/main/java", "app/src/main/kotlin")

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