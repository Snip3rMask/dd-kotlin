package msr.mirudl.shared.network

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import msr.mirudl.shared.model.VideoSource

/**
 * Shared port of the pure-parsing half of `msr.mirudl.app.HlsDownloader` (Java).
 *
 * Only string parsing + Ktor HTTP — no file I/O, no Android APIs.
 * The segment-downloading logic stays in `app` (moves in Phase 4).
 */
object HlsParser {

    const val DEFAULT_PARALLEL = 16
    private const val MAX_PARALLEL = 64

    suspend fun getText(url: String): String {
        val response = HttpClientProvider.get().get(url) {
            headers {
                append(HttpHeaders.UserAgent, "Mozilla/5.0")
                append("Referer", "${MiruClient.BASE}/")
            }
        }
        if (response.status.value != 200) {
            throw Exception("HTTP ${response.status.value}")
        }
        return response.bodyAsText()
    }

    suspend fun qualities(masterUrl: String): List<VideoSource> {
        val master = getText(masterUrl)
        val variants = mutableListOf<VideoSource>()
        var pendingInf: String? = null
        for (raw in master.split("\r?\n")) {
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-STREAM-INF") -> pendingInf = line
                pendingInf != null && line.isNotEmpty() && !line.startsWith("#") -> {
                    variants.add(VideoSource(labelFor(pendingInf), resolve(line, masterUrl)))
                    pendingInf = null
                }
            }
        }
        if (variants.isEmpty()) {
            variants.add(VideoSource("Auto", masterUrl))
        }
        return variants
    }

    fun parseSegments(playlist: String, baseUrl: String): List<String> {
        val segments = mutableListOf<String>()
        for (raw in playlist.split("\r?\n")) {
            val line = raw.trim()
            if (line.isNotEmpty() && !line.startsWith("#")) {
                segments.add(resolve(line, baseUrl))
            }
        }
        return segments
    }

    fun parseInitMap(playlist: String, baseUrl: String): String? {
        for (raw in playlist.split("\r?\n")) {
            val line = raw.trim()
            if (line.startsWith("#EXT-X-MAP")) {
                val urlIdx = line.indexOf("URI=\"")
                if (urlIdx >= 0) {
                    val urlEnd = line.indexOf("\"", urlIdx + 5)
                    if (urlEnd > 0) {
                        return resolve(line.substring(urlIdx + 5, urlEnd), baseUrl)
                    }
                }
            }
        }
        return null
    }

    fun labelFor(infLine: String): String {
        val res = infLine.indexOf("RESOLUTION=")
        if (res >= 0) {
            val x = infLine.indexOf('x', res)
            if (x > res) {
                var end = x + 1
                while (end < infLine.length && infLine[end].isDigit()) end++
                if (end > x + 1) return infLine.substring(x + 1, end) + "p"
            }
        }
        val bw = parseBandwidth(infLine)
        return when {
            bw >= 4500000 -> "1080p"
            bw >= 2200000 -> "720p"
            bw >= 1000000 -> "480p"
            bw > 0 -> "360p"
            else -> "Auto"
        }
    }

    fun parseBandwidth(line: String): Int {
        val idx = line.indexOf("BANDWIDTH=")
        if (idx < 0) return 0
        val start = idx + "BANDWIDTH=".length
        var end = start
        while (end < line.length && line[end].isDigit()) end++
        return line.substring(start, end).toIntOrNull() ?: 0
    }

    fun resolve(value: String, base: String): String {
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        if (value.startsWith("/")) {
            val slash = base.indexOf('/', 8)
            return (if (slash > 0) base.substring(0, slash) else base) + value
        }
        val slash = base.lastIndexOf('/')
        return (if (slash > 0) base.substring(0, slash + 1) else "$base/") + value
    }

    fun sanitize(value: String?): String {
        if (value.isNullOrEmpty()) return "Unknown"
        val cleaned = value
            .replace(Regex("[\\\\/:*?\"<>|]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return if (cleaned.length > 80) cleaned.substring(0, 80).trim() else cleaned
    }

    fun extractParent(fileName: String?): String {
        if (fileName == null) return "Unknown"
        val idx = fileName.indexOf(" - Episode")
        return if (idx > 0) fileName.substring(0, idx) else fileName
    }

    fun clampParallel(value: Int): Int {
        if (value < 1) return 1
        return minOf(value, MAX_PARALLEL)
    }
}
