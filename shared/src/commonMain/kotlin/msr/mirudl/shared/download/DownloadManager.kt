package msr.mirudl.shared.download

import java.util.UUID
import java.util.Collections

/**
 * Shared port of `msr.mirudl.app.DownloadManager` (Java).
 *
 * NO `Serializable` — see migration constraint #5.
 * Job list is a `Collections.synchronizedList` for thread safety.
 * All methods are `synchronized` — same locking strategy as the Java original.
 *
 * Phase 4.1: Job data class + constants.
 * Phase 4.2: Query methods (this step).
 * Phase 4.3a: Mutating methods.
 */
object DownloadManager {

    private val jobs: MutableList<Job> = Collections.synchronizedList(mutableListOf())
    @Volatile private var running = false

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
}
