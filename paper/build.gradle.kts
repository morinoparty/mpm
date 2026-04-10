import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    alias(libs.plugins.run.paper)
    alias(libs.plugins.resource.factory)
    `maven-publish`
}

group = "party.morino"
version = project.version.toString()

dependencies {
    // API module dependency
    implementation(project(":api"))

    // Paper API
    compileOnly(libs.paper.api)

    // Arrow for functional programming
    implementation(libs.arrow.core)
    implementation(libs.arrow.fx.coroutines)

    // Cloud commands
    implementation(libs.bundles.commands)

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kaml)

    // Coroutines
    implementation(libs.bundles.coroutines)

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

    // Test dependencies
    testImplementation(libs.kotlinx.coroutines.test)

    testImplementation(libs.bundles.junit)
    testImplementation(libs.bundles.koin.test)
    testImplementation(libs.bundles.ktor.client)
    testImplementation(libs.commons.io)
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
    runServer {
        minecraftVersion("1.21.10")

        val plugins = runPaper.downloadPluginsSpec {}
        downloadPlugins {
            downloadPlugins.from(plugins)
        }
    }
    test {
        useJUnitPlatform {
            // デフォルトのtestタスクではintegrationタグを除外する
            // （実APIを叩くテストはレート制限・一時的障害で不安定になるため）
            excludeTags("integration")
        }
        testLogging {
            showStandardStreams = true
            events("passed", "skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }

    // 実APIを叩くインテグレーションテスト専用タスク（opt-in実行）
    // 実行例: ./gradlew :paper:integrationTest
    register<Test>("integrationTest") {
        description = "Runs integration tests that hit real upstream APIs (opt-in)."
        group = "verification"

        // testと同じクラスパス・ソースセットを利用
        testClassesDirs = sourceSets.test.get().output.classesDirs
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

sourceSets.main {
    resourceFactory {
        paperPluginYaml {
            name = "mpm"
            version = project.version.toString()
            website = "https://mpm.plugin.morino.party"
            main = "$group.mpm.Mpm"
            apiVersion = "1.20"
            bootstrapper = "$group.mpm.MpmBootstrap"
            loader = "$group.mpm.MpmLoader"
        }
        bukkitPluginYaml {
            name = "mpm"
            version = "miencraft_plugin_version"
            website = "https://mpm.plugin.morino.party"
            main = "$group.mpm.Mpm"
            apiVersion = "1.20"
            libraries = libs.bundles.coroutines.asString()
            softDepend = listOf()
        }
    }
}

fun Provider<MinimalExternalModuleDependency>.asString(): String {
    val dependency = this.get()
    return dependency.module.toString() + ":" + dependency.versionConstraint.toString()
}

fun Provider<ExternalModuleDependencyBundle>.asString(): List<String> =
    this.get().map { dependency ->
        "${dependency.group}:${dependency.name}:${dependency.version}"
    }

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/morinoparty/mpm")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }

    publications {
        create<MavenPublication>("maven") {
            groupId = group.toString()
            artifactId = "mpm-app"
            version = version
            from(components["kotlin"])
        }
    }
}