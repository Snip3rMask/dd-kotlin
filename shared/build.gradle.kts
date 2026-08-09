import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    kotlin("plugin.compose")
    id("com.android.library")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-core:3.0.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                // Compose Multiplatform (shared UI screens)
                implementation("org.jetbrains.compose.material3:material3:1.7.1")
                implementation("org.jetbrains.compose.foundation:foundation:1.7.1")
                implementation("org.jetbrains.compose.runtime:runtime:1.7.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:3.0.3")

                // SAF (DocumentFile) for the download engine's file storage
                // (same version as the app module).
                implementation("androidx.documentfile:documentfile:1.0.1")
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-cio:3.0.3")
            }
        }
    }
}

android {
    namespace = "msr.mirudl.shared"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
