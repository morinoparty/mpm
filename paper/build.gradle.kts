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

    // Test dependencies
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mock.bukkit)
    testImplementation(libs.mockk)
    testImplementation(libs.junit.jupiter)
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
    runServer {
        minecraftVersion("1.21")

        val plugins = runPaper.downloadPluginsSpec {}
        downloadPlugins {
            downloadPlugins.from(plugins)
        }
    }
    test {
        useJUnitPlatform()
        testLogging {
            showStandardStreams = true
            events("passed", "skipped", "failed")
            exceptionFormat = TestExceptionFormat.FULL
        }
    }
}

sourceSets.main {
    resourceFactory {
        paperPluginYaml {
            name = "MinecraftPluginManager"
            version = project.version.toString()
            website = "https://mpm.plugin.morino.party"
            main = "$group.mpm.MinecraftPluginManager"
            apiVersion = "1.20"
            bootstrapper = "$group.mpm.MinecraftPluginManagerBootstrap"
            loader = "$group.mpm.MinecraftPluginManagerLoader"
        }
        bukkitPluginYaml {
            name = "MinecraftPluginManager"
            version = "miencraft_plugin_version"
            website = "https://github.com/Nlkomaru/MinecraftPluginManager"
            main = "$group.mpm.MinecraftPluginManager"
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
            url = uri("https://maven.pkg.github.com/morinoparty/MinecraftPluginManager")
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