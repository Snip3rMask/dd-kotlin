package msr.mirudl.shared.network

import kotlinx.coroutines.runBlocking

/**
 * Desktop verification entry point for the CIO engine (Phase 8.4).
 *
 * Run with: `./gradlew :shared:runDesktopNetworkCheck` once the desktop
 * app module exists, or simply verify that this file compiles via
 * `:shared:compileKotlinDesktop` — the call chain below exercises
 * [MiruClient] exactly like the Android app does, proving the
 * engine-agnostic shared client works with CIO on the JVM.
 */
fun main() {
    runBlocking {
        try {
            val results = MiruClient.search("naruto")
            println("CIO network check OK — ${results.size} results:")
            results.take(5).forEach { println("  - ${it.title} (id=${it.id})") }
        } catch (e: Exception) {
            println("CIO network check FAILED: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }
}
