import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
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

                // JSON (kotlinx-serialization). Version 1.10.0 pinned deliberately:
                // it is compiled with Kotlin 2.3.0, so our Kotlin 2.3.10 compiler
                // reads its metadata cleanly. 1.11.0 is built with Kotlin 2.3.20
                // (newer than ours) — upgrade together with the Kotlin bump later.
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
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
