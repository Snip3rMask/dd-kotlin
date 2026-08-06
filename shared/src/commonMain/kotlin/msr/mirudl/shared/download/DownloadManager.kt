package msr.mirudl.shared.download

import java.util.Collections
import kotlin.random.Random

/**
 * Shared port of `msr.mirudl.app.DownloadManager` (Java).
 *
 * NO `Serializable` — see migration constraint #5.
 * Job list is a `Collections.synchronizedList` for thread safety.
 * All methods are `synchronized` — same locking strategy as the Java original.
 */
object DownloadManager {

    private val jobs: MutableList<Job> = Collections.synchronizedList(mutableListOf())
    private var running = false

    // ── Query methods (read-only) ──────────────────────────────────

    @Synchronized
    fun find(id: String): Job? {
        for (j in jobs) if (id == j.id) return j
        return null
    }

    @Synchronized
    fun findByAnimeAndEpisode(anime: String?, episode: String?): Job? {
        for (j in jobs) {
            if (!j.finished &&
                j.animeTitle != null && j.animeTitle == anime &&
                j.episodeTitle != null && j.episodeTitle == episode) {
                return j
            }
        }
        return null
    }

    @Synchronized
    fun hasActiveJob(anime: String?, episode: String?): Boolean {
        for (j in jobs) {
            if (!j.finished &&
                j.animeTitle != null && j.animeTitle == anime &&
                j.episodeTitle != null && j.episodeTitle == episode) {
                return true
            }
        }
        return false
    }

    @Synchronized
    fun snapshot(): List<Job> {
        return ArrayList(jobs)
    }

    @Synchronized
    fun activeCount(): Int {
        var count = 0
        for (j in jobs) if (!j.finished) count++
        return count
    }

    @Synchronized
    fun queuedCount(): Int {
        var count = 0
        for (j in jobs)
            if (!j.finished && Job.STATUS_QUEUED == j.status) count++
        return count
    }

    @Synchronized
    fun downloadingCount(): Int {
        var count = 0
        for (j in jobs)
            if (!j.finished && Job.STATUS_DOWNLOADING == j.status) count++
        return count
    }

    @Synchronized
    fun isRunning(): Boolean {
        return running
    }

    // ── Mutating methods ───────────────────────────────────────────

    @Synchronized
    fun enqueue(anime: String?, episode: String?, quality: String?,
                language: String?, hlsUrl: String?): Job {
        for (existing in jobs) {
            if (!existing.finished &&
                existing.animeTitle != null && existing.animeTitle == anime &&
                existing.episodeTitle != null && existing.episodeTitle == episode &&
                existing.quality != null && existing.quality == quality) {
                return existing
            }
        }
        val job = Job(anime, episode, quality, language, hlsUrl)
        jobs.add(job)
        return job
    }

    @Synchronized
    fun enqueueAll(newJobs: List<Job>) {
        val seen = linkedSetOf<String>()
        val deduped = mutableListOf<Job>()
        val all = ArrayList(newJobs) + jobs
        for (i in all.indices.reversed()) {
            val j = all[i]
            val key = (j.animeTitle ?: "") + "|||" + (j.episodeTitle ?: "") + "|||" + (j.quality ?: "")
            if (key !in seen || j.finished) {
                seen.add(key)
                deduped.add(0, j)
            }
        }
        jobs.clear()
        jobs.addAll(deduped)
    }

    @Synchronized
    fun update(job: Job?, percent: Int, status: String) {
        if (job == null) return
        job.percent = percent
        job.status = status
    }

    @Synchronized
    fun updateMulti(job: Job?, currentIndex: Int, episode: String, percent: Int, status: String) {
        if (job == null) return
        job.currentIndex = currentIndex
        job.episodeTitle = episode
        job.percent = percent
        job.status = status
    }

    @Synchronized
    fun complete(job: Job?, outputUri: String) {
        if (job == null) return
        job.percent = 100
        job.status = Job.STATUS_COMPLETED
        job.finished = true
        job.bytesPerSecond = 0
        job.outputUri = outputUri
    }

    @Synchronized
    fun fail(job: Job?, error: String?) {
        if (job == null) return
        job.error = error
        job.status = error ?: Job.STATUS_FAILED
        job.finished = true
        job.bytesPerSecond = 0
    }

    @Synchronized
    fun cancel(job: Job?) {
        if (job == null) return
        job.cancelled = true
        job.status = Job.STATUS_CANCELLED
        job.finished = true
        job.bytesPerSecond = 0
    }

    @Synchronized
    fun remove(job: Job?) {
        jobs.remove(job)
    }

    @Synchronized
    fun removeFinished() {
        jobs.removeAll { it.finished }
    }

    @Synchronized
    fun claimNextQueuedJob(): Job? {
        for (j in jobs) {
            if (!j.finished && Job.STATUS_QUEUED == j.status) {
                j.status = Job.STATUS_DOWNLOADING
                j.percent = 0
                return j
            }
        }
        return null
    }

    @Synchronized
    fun setRunning(value: Boolean) {
        running = value
    }
}

/**
 * Generates a UUID-like string for commonMain (no java.util.UUID dependency).
 */
internal fun generateId(): String {
    val chars = "0123456789abcdef"
    return buildString(36) {
        repeat(36) { i ->
            when (i) {
                8, 13, 18, 23 -> append('-')
                else -> append(chars[Random.nextInt(chars.length)])
            }
        }
    }
}

/**
 * Shared Job data class — port of `msr.mirudl.app.DownloadManager.Job` (Java).
 *
 * NO `Serializable` — see migration constraint #5.
 * The 5-arg constructor + auto-generated fields (id, status, etc.)
 * match the Java original exactly.
 */
data class Job @JvmOverloads constructor(
    @JvmField var animeTitle: String? = null,
    @JvmField var episodeTitle: String? = null,
    @JvmField var quality: String? = null,
    @JvmField var language: String? = null,
    @JvmField var hlsUrl: String? = null
) {
    @JvmField val id: String = generateId()
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
