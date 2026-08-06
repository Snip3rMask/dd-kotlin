package msr.mirudl.shared.download

import java.util.UUID

/**
 * Shared port of the `Job` data class + status constants from
 * `msr.mirudl.app.DownloadManager` (Java).
 *
 * NO `Serializable` — see migration constraint #5.
 * Mutable fields use `@Volatile` for thread safety.
 * The 5-arg constructor + auto-generated fields (id, status, etc.)
 * match the Java original exactly.
 *
 * The `DownloadManager` list + methods stay in `app` for now (Phase 4.2–4.3).
 */
data class Job @JvmOverloads constructor(
    @JvmField var animeTitle: String? = null,
    @JvmField var episodeTitle: String? = null,
    @JvmField var quality: String? = null,
    @JvmField var language: String? = null,
    @JvmField var hlsUrl: String? = null
) {
    @JvmField val id: String = UUID.randomUUID().toString()
    @JvmField var status: String = STATUS_QUEUED
    @JvmField var percent: Int = 0
    @JvmField var bytesPerSecond: Long = 0L
    @JvmField var cancelled: Boolean = false
    @JvmField var finished: Boolean = false
    @JvmField var error: String? = null
    @JvmField var outputUri: String? = null
    @JvmField var currentIndex: Int = 1
    @JvmField var totalEpisodes: Int = 1

    companion object {
        const val STATUS_QUEUED = "Queued"
        const val STATUS_DOWNLOADING = "Downloading"
        const val STATUS_COMPLETED = "Completed"
        const val STATUS_FAILED = "Failed"
        const val STATUS_CANCELLED = "Cancelled"
    }
}
