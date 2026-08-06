package msr.mirudl.shared.network

import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.*
import msr.mirudl.shared.storage.AppStorage

/**
 * Shared port of `msr.mirudl.app.UpdateChecker` (Java).
 *
 * Platform-specific concerns (HTTP engine, background thread dispatch,
 * `Handler`/`Looper` posting) live in `UpdateCheckerAndroid` — this
 * file contains only pure logic and cache helpers.
 */
object UpdateChecker {

    private const val REPO = "msrofficial/MiruDL-App"
    private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"

    private const val KEY_TAG = "cached_tag"
    private const val KEY_BODY = "cached_body"
    private const val KEY_URL = "cached_url"
    private const val KEY_APK_URL = "cached_apk_url"
    private const val KEY_LAST_CHECK = "last_check_at"

    const val CACHE_TTL_MS: Long = 3 * 60 * 60 * 1000 // 3 hours

    class ReleaseInfo @JvmOverloads constructor(
        @JvmField var tag: String = "",
        @JvmField var changelog: String = "",
        @JvmField var htmlUrl: String? = null,
        @JvmField var apkUrl: String? = null
    )

    suspend fun fetchLatestRelease(): ReleaseInfo? {
        return try {
            val response = HttpClientProvider.get().get(API_URL) {
                header("Accept", "application/vnd.github+json")
                header("User-Agent", "MiruDL-App")
            }
            if (response.status.value != 200) return null
            val body = response.bodyAsText()
            val root = Json.parseToJsonElement(body) as? JsonObject ?: return null
            val tag = (root["tag_name"] as? JsonPrimitive)?.content?.trim()
                ?: return null
            val changelog = (root["body"] as? JsonPrimitive)?.content?.trim() ?: ""
            val htmlUrl = (root["html_url"] as? JsonPrimitive)?.content?.trim()

            var apkUrl: String? = null
            val assets = root["assets"] as? JsonArray
            if (assets != null) {
                for (element in assets) {
                    val obj = element as? JsonObject ?: continue
                    val name = (obj["name"] as? JsonPrimitive)?.content ?: ""
                    if (name.lowercase().endsWith(".apk")) {
                        apkUrl = (obj["browser_download_url"] as? JsonPrimitive)?.content
                        break
                    }
                }
            }

            ReleaseInfo(
                tag = tag,
                changelog = changelog,
                htmlUrl = htmlUrl,
                apkUrl = apkUrl
            )
        } catch (e: Exception) {
            null
        }
    }

    fun isNewerVersion(remoteTag: String, localVersion: String): Boolean {
        val r = normalize(remoteTag)
        val l = normalize(localVersion)
        val len = maxOf(r.size, l.size)
        for (i in 0 until len) {
            val rv = if (i < r.size) r[i] else 0
            val lv = if (i < l.size) l[i] else 0
            if (rv != lv) return rv > lv
        }
        return false
    }

    fun normalize(v: String?): IntArray {
        if (v == null) return intArrayOf(0)
        var s = v.trim()
        if (s.lowercase().startsWith("v")) s = s.substring(1)
        val parts = s.split(".")
        return IntArray(parts.size) { i ->
            parts[i].replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
        }
    }

    fun readLastCheck(storage: AppStorage): Long {
        return storage.getLong(KEY_LAST_CHECK, 0L)
    }

    fun readCache(storage: AppStorage): ReleaseInfo? {
        val tag = storage.getString(KEY_TAG, "")
        if (tag.isEmpty() || !isNewerVersion(tag, "")) return null
        return ReleaseInfo(
            tag = tag,
            changelog = storage.getString(KEY_BODY, ""),
            htmlUrl = storage.getString(KEY_URL, "").ifEmpty { null },
            apkUrl = storage.getString(KEY_APK_URL, "").ifEmpty { null }
        )
    }

    fun writeCache(storage: AppStorage, info: ReleaseInfo) {
        storage.setString(KEY_TAG, info.tag)
        storage.setString(KEY_BODY, info.changelog)
        storage.setString(KEY_URL, info.htmlUrl ?: "")
        storage.setString(KEY_APK_URL, info.apkUrl ?: "")
        storage.setLong(KEY_LAST_CHECK, System.currentTimeMillis())
    }
}
