package msr.mirudl.shared.network

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.utils.io.errors.IOException
import msr.mirudl.shared.model.AnimeItem
import msr.mirudl.shared.util.htmlToText
import msr.mirudl.shared.util.urlEncode

/**
 * Shared port of `msr.mirudl.app.MiruClient` (Java).
 *
 * The HTTP layer (`get()`, `getHtml()`) was moved in 2.2.
 * 2.3 ports the search functionality. Subsequent steps port the
 * remaining methods (browse, episodes, embed, qualities, …).
 *
 * `BASE` keeps the exact XOR-obfuscation of the original
 * (`https://anidb.app`), and every method mirrors the Java original
 * byte-for-byte — no behaviour changes.
 */
object MiruClient {
    private val encodedBase = byteArrayOf(
        50, 46, 46, 42, 41, 96, 117, 117, 59, 52, 51, 62, 56, 116, 59, 42, 42
    )

    val BASE: String = decodeBase()

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

    private val client get() = HttpClientProvider.get()

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

    // ==================== SEARCH ====================

    suspend fun search(query: String): List<AnimeItem> {
        val url = "$BASE/search/suggestions?q=${urlEncode(query)}"
        val html = getHtml(url)
        return parseSearchResults(html)
    }

    private fun parseSearchResults(html: String): List<AnimeItem> {
        val results = mutableListOf<AnimeItem>()
        var idx = 0
        while (true) {
            val aStart = html.indexOf("<a ", idx)
            if (aStart < 0) break
            val aEnd = html.indexOf("</a>", aStart)
            if (aEnd < 0) break
            val block = html.substring(aStart, aEnd)

            if (block.contains("data-search-item")) {
                val item = AnimeItem()

                // Extract href
                val hrefIdx = block.indexOf("href=\"")
                if (hrefIdx >= 0) {
                    val hrefEnd = block.indexOf("\"", hrefIdx + 6)
                    if (hrefEnd > 0) {
                        item.url = block.substring(hrefIdx + 6, hrefEnd)
                        val parts = item.url!!.split("-")
                        item.id = parts.last()
                    }
                }

                // Extract title
                val titleStart = block.indexOf("line-clamp-1\">")
                if (titleStart >= 0) {
                    val titleEnd = block.indexOf("</p>", titleStart)
                    if (titleEnd > 0) {
                        item.title = block.substring(titleStart + 14, titleEnd).trim()
                        item.title = htmlToText(item.title!!)
                    }
                }

                // Extract poster
                val imgIdx = block.indexOf("src=\"")
                if (imgIdx >= 0) {
                    val imgEnd = block.indexOf("\"", imgIdx + 5)
                    if (imgEnd > 0) {
                        item.thumbnail = block.substring(imgIdx + 5, imgEnd)
                    }
                }

                if (item.id != null) results.add(item)
            }
            idx = aEnd + 4
        }
        return results
    }
}
