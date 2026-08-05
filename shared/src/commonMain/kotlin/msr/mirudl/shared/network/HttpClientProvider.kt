package msr.mirudl.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout

/**
 * Single shared, pre-configured [HttpClient] for the whole app.
 *
 * Engine is chosen per target at build time via `build.gradle.kts`
 * (`ktor-client-okhttp` on Android, `ktor-client-cio` on Desktop, Darwin
 * engine later for iOS) — this provider stays engine-agnostic.
 *
 * Timeouts mirror the original OkHttp builder in `MiruClient.java`
 * (connect/read 30s) and redirects are followed, same as today.
 */
object HttpClientProvider {
    private val client: HttpClient by lazy { create() }

    fun get(): HttpClient = client

    private fun create(): HttpClient = HttpClient {
        followRedirects = true
        expectSuccess = false
        install(HttpTimeout) {
            connectTimeoutMillis = 30_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }
    }
}
