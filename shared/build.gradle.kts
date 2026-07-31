import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
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
                // Multiplatform HTTP client core — engine-agnostic on purpose.
                // Real engines (OkHttp for Android, CIO for Desktop, Darwin for
                // iOS later) get added when networking code actually moves
                // here in Phase 2. This step just proves the dependency
                // resolves for every target we have configured so far.
                implementation("io.ktor:ktor-client-core:3.5.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val androidMain by getting
        val desktopMain by getting
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
