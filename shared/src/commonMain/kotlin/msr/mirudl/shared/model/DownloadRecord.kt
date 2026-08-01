package msr.mirudl.shared.model

/**
 * A single completed-download record, as persisted to storage.
 *
 * This was the inner class `msr.mirudl.app.DownloadEntryStore.Entry`
 * (Java) — pulled out to its own top-level shared model and renamed to
 * `DownloadRecord` to avoid colliding with the *different*, Android-only
 * `msr.mirudl.app.DownloadEntry` (a UI-facing wrapper around this record
 * that adds a parsed `Uri` + SAF delete methods — that class stays in
 * `app` since it depends on `androidx.documentfile`).
 *
 * The persistence logic itself (`DownloadEntryStore`'s SharedPreferences
 * read/write) stays in `app` for now too — it moves to `shared` in
 * Phase 3 once the multiplatform storage abstraction exists. This step
 * only moves the plain data shape.
 *
 * `@JvmField` keeps every property a plain public field on the JVM (so
 * existing Java call sites like `e.uri = "..."` keep working unchanged),
 * and `@JvmOverloads` generates a no-arg constructor overload so
 * `new DownloadRecord()` (used in `HlsDownloader`) still works from Java.
 */
data class DownloadRecord @JvmOverloads constructor(
    @JvmField var uri: String? = null,
    @JvmField var filePath: String? = null,
    @JvmField var title: String? = null,
    @JvmField var parent: String? = null,
    @JvmField var size: Long = 0L,
    @JvmField var completedAt: Long = 0L
) {
    fun key(): String? {
        if (!uri.isNullOrEmpty()) return uri
        return filePath ?: title
    }

    fun parentName(): String {
        return if (!parent.isNullOrBlank()) parent!! else "MiruDL"
    }
}
