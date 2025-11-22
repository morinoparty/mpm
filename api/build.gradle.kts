import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    `maven-publish`
}

group = "party.morino"
version = project.version.toString()

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
            artifactId = "mpm-api"
            version = version
            from(components["kotlin"])
        }
    }
}

dependencies {
    // Arrow for functional programming
    implementation(libs.arrow.core)
    implementation(libs.arrow.fx.coroutines)

    // Koin for dependency injection
    implementation(libs.koin.core)

    // Kotlinx Serialization for JSON handling
    implementation(libs.kotlinx.serialization.json)

    // Ktor client for HTTP requests
    implementation(libs.bundles.ktor.client)

    // Kotlin reflection
    implementation(libs.kotlin.reflect)

    // SnakeYAML for YAML parsing
    implementation(libs.snakeyaml)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
}

kotlin {
    jvmToolchain {
        (this).languageVersion.set(JavaLanguageVersion.of(21))
    }
    jvmToolchain(21)
}

tasks {
    compileKotlin {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
        compilerOptions.javaParameters = true
    }
}

repositories {
    mavenCentral()
}