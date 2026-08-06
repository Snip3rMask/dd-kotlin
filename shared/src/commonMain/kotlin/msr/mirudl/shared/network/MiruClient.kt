package msr.mirudl.shared.network

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.*
import io.ktor.utils.io.errors.IOException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import msr.mirudl.shared.model.AnimeItem
import msr.mirudl.shared.model.EpisodeItem
import msr.mirudl.shared.util.htmlToText
import msr.mirudl.shared.util.urlEncode

/**
 * Shared port of `msr.mirudl.app.MiruClient` (Java).
 *
 * The HTTP layer (`get()`, `getHtml()`) was moved in 2.2.
 * 2.3 ports search; 2.4 ports browse; 2.5 ports episodes.
 * Subsequent steps port embed/HLS, qualities, helpers.
 *
 * JSON parsing uses `kotlinx.serialization` instead of `org.json`
 * (which is Android/JVM-only). `@Serializable` DTOs match the
 * original `optInt`/`optBoolean` defaults for absent fields.
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

    /** Reusable Json instance — ignores unknown fields for forward compat. */
    private val json = Json { ignoreUnknownKeys = true }

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

                val hrefIdx = block.indexOf("href=\"")
                if (hrefIdx >= 0) {
                    val hrefEnd = block.indexOf("\"", hrefIdx + 6)
                    if (hrefEnd > 0) {
                        item.url = block.substring(hrefIdx + 6, hrefEnd)
                        val parts = item.url!!.split("-")
                        item.id = parts.last()
                    }
                }

                val titleStart = block.indexOf("line-clamp-1\">")
                if (titleStart >= 0) {
                    val titleEnd = block.indexOf("</p>", titleStart)
                    if (titleEnd > 0) {
                        item.title = block.substring(titleStart + 14, titleEnd).trim()
                        item.title = htmlToText(item.title!!)
                    }
                }

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

    // ==================== BROWSE ====================

    suspend fun browseCurrentlyAiring(): List<AnimeItem> {
        val url = "$BASE/browse?sort=order_top_airing&status=Currently+Airing"
        val html = getHtml(url)
        return parseBrowseResults(html)
    }

    private fun parseBrowseResults(html: String): List<AnimeItem> {
        val results = mutableListOf<AnimeItem>()
        var idx = 0
        while (true) {
            val linkStart = html.indexOf("/anime/", idx)
            if (linkStart < 0) break

            val aStart = html.lastIndexOf("<a ", linkStart)
            if (aStart < 0 || aStart < idx - 50) {
                idx = linkStart + 7
                continue
            }

            val aEnd = html.indexOf("</a>", linkStart)
            if (aEnd < 0) break

            val block = html.substring(aStart, aEnd + 4)
            val item = AnimeItem()

            val hrefEnd = html.indexOf("\"", linkStart + 7)
            if (hrefEnd > linkStart) {
                item.url = BASE + html.substring(linkStart, hrefEnd)
                val slugPart = if (item.url!!.startsWith("/anime/")) item.url!!.substring(7) else item.url!!
                val parts = slugPart.split("-")
                item.id = if (parts.isNotEmpty()) parts.last() else null
            }

            var altIdx = block.indexOf("alt=\"")
            if (altIdx >= 0) {
                altIdx += 5
                val altEnd = block.indexOf("\"", altIdx)
                if (altEnd > altIdx) {
                    val alt = block.substring(altIdx, altEnd).trim()
                    if (alt.isNotEmpty() && !alt.lowercase().contains("thumbnail")
                        && !alt.lowercase().contains("poster")
                    ) {
                        item.title = htmlToText(alt)
                    }
                }
            }

            if (item.title == null || item.title!!.isEmpty()) {
                val regex = Regex("""<(h[23]|p)\b[^>]*>(.*?)</\1>""", RegexOption.DOT_MATCHES_ALL)
                val match = regex.find(block)
                if (match != null) {
                    val t = htmlToText(match.groupValues[2]).trim()
                    if (t.isNotEmpty() && t.length < 100) item.title = t
                }
            }
            if (item.title == null) item.title = ""

            var imgIdx = block.indexOf("src=\"")
            if (imgIdx >= 0) {
                imgIdx += 5
                val imgEnd = block.indexOf("\"", imgIdx)
                if (imgEnd > imgIdx) {
                    val src = block.substring(imgIdx, imgEnd)
                    item.thumbnail = if (!src.startsWith("http")) {
                        if (src.startsWith("/")) BASE + src else "$BASE/$src"
                    } else {
                        src
                    }
                }
            }

            if (item.id != null && item.title!!.isNotEmpty()) {
                val dup = results.any { it.id != null && it.id == item.id }
                if (!dup) results.add(item)
            }

            idx = aEnd + 4
        }
        return results
    }

    // ==================== EPISODES ====================

    suspend fun getEpisodes(animeId: String): List<EpisodeItem> {
        val url = "$BASE/api/frontend/anime/$animeId/episodes"
        val body = get(url)
        val response = json.decodeFromString<EpisodesResponse>(body)
        return response.episodes.map { ep ->
            EpisodeItem(
                id = ep.id,
                number = ep.number,
                number2 = ep.number2,
                filler = ep.filler
            )
        }
    }

    /**
     * Currently identical to [getEpisodes] — kept as a separate entry
     * point for API parity with the Java original.
     */
    suspend fun getEpisodesWithSeasons(animeId: String): List<EpisodeItem> =
        getEpisodes(animeId)

    // -- kotlinx.serialization DTOs (private) --

    @Serializable
    private data class EpisodesResponse(val episodes: List<EpisodeDto> = emptyList())

    @Serializable
    private data class EpisodeDto(
        val id: Int = 0,
        val number: Int = 0,
        val number2: Int = 0,
        val filler: Boolean = false
    )
}
