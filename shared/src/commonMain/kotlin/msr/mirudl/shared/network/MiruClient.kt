package msr.mirudl.shared.network

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.utils.io.errors.IOException

/**
 * Ported HTTP layer of the original `msr.mirudl.app.MiruClient` (Java).
 * Only the HTTP helpers live here for now — the parsing methods
 * (`search`, `getEpisodes`, ...) move over in Phase 2.3–2.7.
 *
 * `BASE` keeps the exact XOR-obfuscation of the original
 * (`https://anidb.app`), and headers mirror the old OkHttp requests
 * byte-for-byte.
 */
object MiruClient {
    // Encoded base URL to deter casual decompilation (same bytes as the Java original).
    private val encodedBase = byteArrayOf(
        50, 46, 46, 42, 41, 96, 117, 117, 59, 52, 51, 62, 56, 116, 59, 42, 42
    )

    val BASE: String = decodeBase()

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

    private val client: HttpClient get() = HttpClientProvider.get()

    private fun decodeBase(): String {
        val decoded = ByteArray(encodedBase.size)
        for (i in encodedBase.indices) {
            decoded[i] = (encodedBase[i].toInt() xor 0x5A).toByte()
        }
        return decoded.toString(Charsets.UTF_8)
    }

    /** JSON GET — same headers as the original `get()`. */
    suspend fun get(url: String): String = request(url, "application/json", addXRequestedWith = true)

    /** HTML GET — same headers as the original `getHtml()`. */
    suspend fun getHtml(url: String): String = request(url, "text/html,*/*", addXRequestedWith = false)

    private suspend fun request(url: String, accept: String, addXRequestedWith: Boolean): String {
        val response = client.get(url) {
            headers {
                append(HttpHeaders.UserAgent, UA)
                append(HttpHeaders.Accept, accept)
                if (addXRequestedWith) {
                    append("X-Requested-With", "XMLHttpRequest")
                }
                append("Referer", "$BASE/")
            }
        }
        if (response.status.value !in 200..299) {
            throw IOException("HTTP ${response.status.value}")
        }
        return response.bodyAsText()
    }
}
