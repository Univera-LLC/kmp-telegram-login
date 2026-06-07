import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.vanniktechMavenPublish)
}

val libVersion = "0.1.0"

kotlin {
    jvmToolchain(25)
    explicitApi()

    compilerOptions {
        // expect/actual classes are still Beta — silence the warning.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    android {
        namespace = "app.univera.telegramlogin"
        compileSdk = 37
        minSdk = 28
        // Run commonTest as JVM host unit tests: ./gradlew :telegram-login:testAndroidHostTest
        withHostTest {}
    }

    val xcframework = XCFramework("TelegramLogin")
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        iosX64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "TelegramLogin"
            isStatic = true
            xcframework.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.okio)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.browser)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

mavenPublishing {
    // No-arg defaults to the Sonatype Central Portal (accounts created after 2024-03).
    publishToMavenCentral()

    // Sign only when keys are present (CI / release) so local builds and
    // publishToMavenLocal keep working without GPG configured.
    if (providers.gradleProperty("signingInMemoryKey").isPresent ||
        providers.gradleProperty("signing.keyId").isPresent
    ) {
        signAllPublications()
    }

    coordinates("app.univera.telegramlogin", "telegram-login", libVersion)

    pom {
        name = "Telegram Login KMP"
        description = "Kotlin Multiplatform SDK for Telegram native login (OAuth2 + PKCE) on Android and iOS."
        inceptionYear = "2026"
        url = "https://github.com/Univera-LLC/kmp-telegram-login"
        licenses {
            license {
                name = "MIT License"
                url = "https://opensource.org/licenses/MIT"
                distribution = "https://opensource.org/licenses/MIT"
            }
        }
        developers {
            developer {
                id = "univera"
                name = "Univera LLC"
                url = "https://univera.app"
            }
        }
        scm {
            url = "https://github.com/Univera-LLC/kmp-telegram-login"
            connection = "scm:git:git://github.com/Univera-LLC/kmp-telegram-login.git"
            developerConnection = "scm:git:ssh://git@github.com/Univera-LLC/kmp-telegram-login.git"
        }
    }
}
